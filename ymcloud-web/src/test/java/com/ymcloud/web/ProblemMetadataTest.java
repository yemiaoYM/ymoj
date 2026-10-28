package com.ymcloud.web;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.web.service.admin.impl.AdminProblemServiceImpl;
import com.ymcloud.web.service.admin.AdminProblemService;
import com.ymcloud.web.controller.admin.problem.AdminProblemController;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
     * 新增和编辑均接受 1024 MB，并拒绝超过上限及缺失的内存限制。
     * @throws Exception MVC 请求失败
     */
    @Test
    void validatesMemoryLimitAtHttpBoundary() throws Exception {
        AdminProblemService service = mock(AdminProblemService.class);
        AdminProblemController controller = new AdminProblemController();
        ReflectionTestUtils.setField(controller, "adminProblemService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        ProblemDTO dto = request();
        for (Long memory : new Long[]{256L, 512L, 1024L}) {
            dto.setMemoryLimit(memory);
            mvc.perform(post("/admin/problem").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(dto))).andExpect(status().isOk());
            mvc.perform(put("/admin/problem/10").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(dto))).andExpect(status().isOk());
        }
        for (Long memory : new Long[]{null, 255L, 1025L}) {
            dto.setMemoryLimit(memory);
            mvc.perform(post("/admin/problem").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(dto))).andExpect(status().isBadRequest());
            mvc.perform(put("/admin/problem/10").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(dto))).andExpect(status().isBadRequest());
        }
        verify(service, times(3)).addProblem(any(ProblemDTO.class));
        verify(service, times(3)).updateProblem(eq(10L), any(ProblemDTO.class));
    }

    /**
     * 来源只作说明，新增题目保留已校验的自定义展示 ID。
     */
    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomIdIndependentlyOfSource() {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        LambdaQueryChainWrapper<Problem> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(problems.lambdaQuery()).thenReturn(query);
        when(problems.save(any(Problem.class))).thenReturn(true);
        AdminProblemServiceImpl service = service(problems, mock(ProblemTagEntityService.class));
        ProblemDTO dto = request();
        dto.setProblemId("Custom_1000");
        dto.setMemoryLimit(1024L);
        dto.setSource("  此题来源于洛谷 P1000  ");
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user);
            assertEquals("Custom_1000", service.addProblem(dto));
        }
        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problems).save(saved.capture());
        assertEquals("Custom_1000", saved.getValue().getProblemId());
        assertEquals("此题来源于洛谷 P1000", saved.getValue().getSource());
        assertEquals("default", saved.getValue().getJudgeMode());
        assertEquals(1048576L, saved.getValue().getMemoryLimit());
    }

    /**
     * 清空来源和样例会实际保存，但保留历史题号和作者。
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
        existing.setExamples("[{\"input\":\"1 2\",\"output\":\"3\"}]");
        when(problems.getById(10L)).thenReturn(existing);
        when(problems.updateById(any(Problem.class))).thenReturn(true);
        ProblemDTO dto = request();
        dto.setProblemId("YMOJ-9999");
        dto.setSource(source);
        dto.setProblemCaseList(List.of());
        service(problems, tags).updateProblem(10L, dto);
        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problems).updateById(saved.capture());
        assertNull(saved.getValue().getSource());
        assertEquals("P1000", saved.getValue().getProblemId());
        assertEquals(2L, saved.getValue().getAuthorId());
        assertEquals("[]", saved.getValue().getExamples());
    }

    /**
     * 验证 HTTP 新增分组的字符、长度及必填边界；编辑不拒绝历史题号。
     * 这里仅隔离验证参数绑定，现有鉴权注解不在独立 MVC 环境中执行。
     * @throws Exception MVC 请求失败
     */
    @Test
    void validatesCustomIdAtHttpBoundary() throws Exception {
        AdminProblemService service = mock(AdminProblemService.class);
        AdminProblemController controller = new AdminProblemController();
        ReflectionTestUtils.setField(controller, "adminProblemService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        ProblemDTO dto = request();
        for (String id : new String[]{null, "", " ", "A-B", "中文", "A/B", "A B", "A".repeat(16)}) {
            dto.setProblemId(id);
            mvc.perform(post("/admin/problem").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(dto))).andExpect(status().isBadRequest());
        }
        // Create 必须继承通用约束，不能只验证题号
        dto.setProblemId("P1000");
        dto.setTitle(" ");
        mvc.perform(post("/admin/problem").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(dto))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        dto.setTitle("A + B");
        for (String id : new String[]{"A", "123", "Custom_1000", "A".repeat(15)}) {
            dto.setProblemId(id);
            mvc.perform(post("/admin/problem").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(dto))).andExpect(status().isOk());
        }
        verify(service, times(4)).addProblem(any(ProblemDTO.class));
        dto.setProblemId("YMOJ-1000");
        mvc.perform(put("/admin/problem/10").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(dto))).andExpect(status().isOk());
        verify(service).updateProblem(eq(10L), any(ProblemDTO.class));

        // 样例最多3组，标签仍允许5个
        // 新增和编辑使用相同的样例上限
        dto.setProblemId("P1000");
        dto.setTagList(List.of(1L, 2L, 3L, 4L, 5L));
        dto.setProblemCaseList(Collections.nCopies(3, new ProblemCaseVO()));
        mvc.perform(post("/admin/problem").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(dto))).andExpect(status().isOk());
        dto.setProblemCaseList(Collections.nCopies(4, new ProblemCaseVO()));
        mvc.perform(post("/admin/problem").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(dto))).andExpect(status().isBadRequest());
        mvc.perform(put("/admin/problem/10").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(dto))).andExpect(status().isBadRequest());
        verify(service, times(5)).addProblem(any(ProblemDTO.class));
        verify(service).updateProblem(eq(10L), any(ProblemDTO.class));
    }

    /**
     * 已存在的题号在写入前拒绝，数据库唯一冲突也转成同一业务错误并保留原因。
     */
    @Test
    @SuppressWarnings("unchecked")
    void rejectsDuplicateIdsIncludingConcurrentInsert() {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        ProblemTagEntityService tags = mock(ProblemTagEntityService.class);
        LambdaQueryChainWrapper<Problem> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(problems.lambdaQuery()).thenReturn(query);
        when(query.exists()).thenReturn(true, false);
        AdminProblemServiceImpl service = service(problems, tags);
        ProblemException duplicate = assertThrows(ProblemException.class, () -> service.addProblem(request()));
        assertEquals(HttpStatus.CONFLICT, duplicate.getCode());
        verify(problems, never()).save(any(Problem.class));

        DuplicateKeyException cause = new DuplicateKeyException("test unique constraint");
        when(problems.save(any(Problem.class))).thenThrow(cause);
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUser).thenReturn(user);
            ProblemException concurrent = assertThrows(ProblemException.class, () -> service.addProblem(request()));
            assertEquals(HttpStatus.CONFLICT, concurrent.getCode());
            assertSame(cause, concurrent.getCause());
        }
        verifyNoInteractions(tags);
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
     * 构造可通过新增接口校验的题目。
     * @return 普通判题请求
     */
    private ProblemDTO request() {
        ProblemDTO dto = new ProblemDTO();
        dto.setProblemId("P1000");
        dto.setTitle("A + B");
        dto.setContent("计算两个整数之和");
        dto.setInput("两个整数");
        dto.setOutput("输出和");
        dto.setVisibility(1);
        dto.setOpenCaseResult(false);
        dto.setTimeLimit(1000L);
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
