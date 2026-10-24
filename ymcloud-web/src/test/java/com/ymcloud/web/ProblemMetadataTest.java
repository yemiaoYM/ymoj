package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.web.service.admin.impl.AdminProblemServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-25
 * @Description: 题目难度、来源、题号与判题方式的边界回归。
 */
class ProblemMetadataTest {

    /**
     * 难度只接受五级，来源可空且有长度上限，判题方式有固定取值。
     */
    @Test
    void validatesMetadataContract() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            ProblemDTO dto = new ProblemDTO();
            for (int difficulty : new int[]{1, 2, 3, 4, 5}) {
                dto.setDifficulty(difficulty);
                assertTrue(validator.validateProperty(dto, "difficulty").isEmpty());
            }
            for (Integer difficulty : new Integer[]{null, 0, 6}) {
                dto.setDifficulty(difficulty);
                assertFalse(validator.validateProperty(dto, "difficulty").isEmpty());
            }
            for (String source : new String[]{null, "", "此题来源于洛谷 P1000", "题".repeat(255)}) {
                dto.setSource(source);
                assertTrue(validator.validateProperty(dto, "source").isEmpty());
            }
            dto.setSource("题".repeat(256));
            assertFalse(validator.validateProperty(dto, "source").isEmpty());
            for (String mode : new String[]{"default", "spj", "interactive"}) {
                dto.setJudgeMode(mode);
                assertTrue(validator.validateProperty(dto, "judgeMode").isEmpty());
            }
            for (String mode : new String[]{null, "", "0", "DEFAULT", "unknown"}) {
                dto.setJudgeMode(mode);
                assertFalse(validator.validateProperty(dto, "judgeMode").isEmpty());
            }
        }
    }

    /**
     * 来源是说明文本，不参与题号生成；新增题目忽略外部指定题号。
     */
    @Test
    @SuppressWarnings("unchecked")
    void generatesLocalIdIndependentlyOfSource() {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        LambdaQueryChainWrapper<Problem> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(problems.lambdaQuery()).thenReturn(query);
        when(problems.selectLatestLocalProblemIdForUpdate()).thenReturn("YMOJ-1006");
        when(problems.save(any(Problem.class))).thenReturn(true);
        AdminProblemServiceImpl service = service(problems, mock(ProblemTagEntityService.class));
        ProblemDTO dto = request();
        dto.setProblemId("P1000");
        dto.setSource("  此题来源于洛谷 P1000  ");
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user);
            assertEquals("YMOJ-1007", service.addProblem(dto));
        }
        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problems).save(saved.capture());
        assertEquals("YMOJ-1007", saved.getValue().getProblemId());
        assertEquals("此题来源于洛谷 P1000", saved.getValue().getSource());
        assertEquals("default", saved.getValue().getJudgeMode());
        assertEquals(262144L, saved.getValue().getMemoryLimit());
    }

    /**
     * 清空来源产生 null 更新，但保留历史题号和作者。
     * @param source 空来源输入
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @SuppressWarnings("unchecked")
    void clearingSourcePreservesExistingIdentity(String source) {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        ProblemTagEntityService tags = mock(ProblemTagEntityService.class);
        LambdaUpdateChainWrapper<ProblemTag> update = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        when(update.eq(any(), any())).thenReturn(update);
        when(tags.lambdaUpdate()).thenReturn(update);
        Problem existing = new Problem();
        existing.setProblemId("P1000");
        existing.setAuthorId(2L);
        existing.setSource("原题出处");
        when(problems.getById(10L)).thenReturn(existing);
        when(problems.updateById(any(Problem.class))).thenReturn(true);
        ProblemDTO dto = request();
        dto.setProblemId("YMOJ-9999");
        dto.setSource(source);
        service(problems, tags).updateProblem(10L, dto);
        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problems).updateById(saved.capture());
        assertNull(saved.getValue().getSource());
        assertEquals("P1000", saved.getValue().getProblemId());
        assertEquals(2L, saved.getValue().getAuthorId());
        verify(problems, never()).selectLatestLocalProblemIdForUpdate();
    }

    /**
     * 尚未实现的模式在任何数据库读写前被拒绝。
     * @param mode 不受支持的判题方式
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"spj", "interactive", "unknown"})
    void unsupportedModesNeverWrite(String mode) {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        ProblemTagEntityService tags = mock(ProblemTagEntityService.class);
        AdminProblemServiceImpl service = service(problems, tags);
        ProblemDTO dto = request();
        dto.setJudgeMode(mode);
        assertThrows(ProblemException.class, () -> service.addProblem(dto));
        assertThrows(ProblemException.class, () -> service.updateProblem(10L, dto));
        verifyNoInteractions(problems, tags);
    }

    /**
     * 构造服务方法使用的最小有效元数据。
     * @return 普通判题请求
     */
    private ProblemDTO request() {
        ProblemDTO dto = new ProblemDTO();
        dto.setJudgeMode("default");
        dto.setDifficulty(1);
        dto.setMemoryLimit(256L);
        return dto;
    }

    /**
     * 隔离数据库，只验证业务规则。
     * @param problems 题目实体服务
     * @param tags 题目标签实体服务
     * @return 注入依赖后的业务服务
     */
    private AdminProblemServiceImpl service(ProblemEntityService problems, ProblemTagEntityService tags) {
        AdminProblemServiceImpl service = new AdminProblemServiceImpl();
        ReflectionTestUtils.setField(service, "problemEntityService", problems);
        ReflectionTestUtils.setField(service, "problemTagEntityService", tags);
        return service;
    }
}
