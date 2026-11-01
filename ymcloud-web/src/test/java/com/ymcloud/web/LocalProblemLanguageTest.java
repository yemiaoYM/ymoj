package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.vo.oj.LanguageVO;
import com.ymcloud.pojo.vo.oj.ProblemVO;
import com.ymcloud.web.service.oj.impl.ContestServiceImpl;
import com.ymcloud.web.service.oj.impl.ProblemServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-23
 * @Description: 验证原题出处不影响普通题目及比赛题目的本站语言选项。
 */
class LocalProblemLanguageTest {

    /**
     * 所有出处的普通题目均返回本站语言模板。
     * @param source 原题出处
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"此题来源于洛谷 P1000", "AtCoder ABC100 A", "Codeforces 1A", "LeetCode 两数之和"})
    void problemDetailsUseLocalLanguages(String source) {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        LanguageEntityService languages = mock(LanguageEntityService.class);
        Problem problem = problem(source);
        doReturn(query(problem)).when(problems).lambdaQuery();
        List<LanguageVO> enabled = List.of(new LanguageVO());
        when(languages.getByStatusAndOj(LanguageConstants.ABLE, LanguageConstants.LOCAL_PLATFORM))
                .thenReturn(enabled);
        ProblemServiceImpl service = new ProblemServiceImpl();
        ReflectionTestUtils.setField(service, "resourceAccess", mock(ResourceAccessService.class));
        ReflectionTestUtils.setField(service, "problemEntityService", problems);
        ReflectionTestUtils.setField(service, "languageEntityService", languages);
        ReflectionTestUtils.setField(service, "judgeEntityService", judges());
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            ProblemVO response = service.getProblemInfo(problem.getProblemId());
            assertEquals(enabled, response.getLanguageList());
            assertEquals(source, response.getSource());
            assertEquals(problem.getProblemId(), response.getProblemId());
        }
        verify(languages).getByStatusAndOj(LanguageConstants.ABLE, LanguageConstants.LOCAL_PLATFORM);
        verifyNoMoreInteractions(languages);
    }

    /**
     * 比赛题目保留比赛展示号与语言模板，但不返回原题来源。
     * @param source 原题出处
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"此题来源于洛谷 P1000", "AtCoder ABC100 A", "Codeforces 1A", "LeetCode 两数之和"})
    void contestProblemDetailsUseLocalLanguages(String source) {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        ContestEntityService contests = mock(ContestEntityService.class);
        ContestProblemEntityService mappings = mock(ContestProblemEntityService.class);
        LanguageEntityService languages = mock(LanguageEntityService.class);
        Problem problem = problem(source);
        when(problems.getById(problem.getId())).thenReturn(problem);
        Contest contest = new Contest();
        contest.setId(20L);
        contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
        contest.setStartTime(LocalDateTime.now().minusMinutes(1));
        when(contests.getById(contest.getId())).thenReturn(contest);
        ContestProblem mapping = new ContestProblem();
        mapping.setProblemId(problem.getId());
        mapping.setDisplayId("A");
        mapping.setDisplayTitle("比赛题目");
        doReturn(query(mapping)).when(mappings).lambdaQuery();
        List<LanguageVO> enabled = List.of(new LanguageVO());
        when(languages.getByStatusAndOj(LanguageConstants.ABLE, LanguageConstants.LOCAL_PLATFORM))
                .thenReturn(enabled);
        ContestServiceImpl service = new ContestServiceImpl();
        ReflectionTestUtils.setField(service, "resourceAccess", mock(ResourceAccessService.class));
        ReflectionTestUtils.setField(service, "problemEntityService", problems);
        ReflectionTestUtils.setField(service, "contestEntityService", contests);
        ReflectionTestUtils.setField(service, "contestProblemEntityService", mappings);
        ReflectionTestUtils.setField(service, "languageEntityService", languages);
        ReflectionTestUtils.setField(service, "judgeEntityService", judges());
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            ProblemVO response = service.getContestProblem(contest.getId(), "A");
            assertEquals(enabled, response.getLanguageList());
            assertNull(response.getSource());
            assertEquals(source, problem.getSource());
            assertEquals("A", response.getProblemId());
            assertEquals(mapping.getDisplayTitle(), response.getTitle());
        }
        verify(languages).getByStatusAndOj(LanguageConstants.ABLE, LanguageConstants.LOCAL_PLATFORM);
        verifyNoMoreInteractions(languages);
    }

    /**
     * 创建无需作者权限的公开题目。
     * @param source 原题出处
     * @return 测试题目
     */
    private Problem problem(String source) {
        Problem problem = new Problem();
        problem.setId(10L);
        problem.setProblemId("P1000");
        problem.setSource(source);
        problem.setVisibility(ProblemConstants.PUBLIC);
        problem.setExamples("[]");
        return problem;
    }

    /**
     * 创建没有通过记录的实体服务。
     * @return 隔离数据库的提交查询服务
     */
    private JudgeEntityService judges() {
        JudgeEntityService judges = mock(JudgeEntityService.class);
        doReturn(query((Judge) null)).when(judges).lambdaQuery();
        return judges;
    }

    /**
     * 隔离数据库，保留服务层条件链及结果组装。
     * @param value 单条查询结果
     * @param <T> 实体类型
     * @return 可链式调用的查询替身
     */
    @SuppressWarnings("unchecked")
    private <T> LambdaQueryChainWrapper<T> query(T value) {
        LambdaQueryChainWrapper<T> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(value);
        return query;
    }
}
