package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotAccessException;
import com.ymcloud.common.exception.contest.ContestNotStartException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.exception.submission.SubmissionException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.service.oj.impl.JudgeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 使用真实认证上下文验证题目状态、原作者例外、比赛资格和代码访问矩阵
 */
class ResourcePermissionTest {

    private final ResourceAccessService access = new ResourceAccessService();
    private final ProblemEntityService problems = mock(ProblemEntityService.class);
    private final ContestEntityService contests = mock(ContestEntityService.class);
    private final ContestUserEntityService users = mock(ContestUserEntityService.class);
    private Problem problem;
    private Contest contest;

    /**
     * 为每个用例准备他人的原题和公开比赛
     */
    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(access, "problems", problems);
        ReflectionTestUtils.setField(access, "contests", contests);
        ReflectionTestUtils.setField(access, "contestUsers", users);
        problem = new Problem();
        problem.setId(10L);
        problem.setAuthorId(8L);
        problem.setVisibility(ProblemConstants.PRIVATE);
        contest = new Contest();
        contest.setId(20L);
        contest.setCreatorId(8L);
        contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
        contest.setStartTime(LocalDateTime.now().minusMinutes(1));
        contest.setEndTime(LocalDateTime.now().plusMinutes(1));
        when(problems.getById(10L)).thenReturn(problem);
        when(contests.getById(20L)).thenReturn(contest);
    }

    /**
     * 清除线程认证，避免权限在用例间泄漏
     */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 三种状态按角色和活动引用判断
     * 非管理用户不因作者 ID 保留私有访问
     * @param id 当前用户 ID
     * @param role 当前角色
     * @param visibility 持久状态
     * @param active 是否仍有未结束普通比赛引用
     * @param allowed 是否允许普通题库访问
     */
    @ParameterizedTest
    @CsvSource({
            "1, USER, 3, true, true", "9, ADMIN, 3, true, true",
            "9, PROBLEM_SETTER, 1, false, true", "9, PROBLEM_SETTER, 2, false, true",
            "9, PROBLEM_SETTER, 3, true, false", "9, PROBLEM_SETTER, 3, false, true",
            "9, CONTEST_MANAGER, 2, false, true", "9, CONTEST_MANAGER, 1, true, false",
            "8, PROBLEM_SETTER, 3, true, true", "8, CONTEST_MANAGER, 3, true, true",
            "8, USER, 2, false, false", "9, USER, 1, false, true",
            "9, USER, 2, false, false", "9, USER, 3, false, false",
            "9, PROBLEM_ADMIN, 2, false, false"
    })
    void problemMatrix(long id, String role, int visibility, boolean active, boolean allowed) {
        login(id, role);
        problem.setVisibility(visibility);
        when(problems.hasActiveContestReference(eq(10L), any())).thenReturn(active);
        if (allowed) {
            assertDoesNotThrow(() -> access.requireReadProblem(problem));
        } else {
            assertThrows(ProblemPermissionException.class, () -> access.requireReadProblem(problem));
        }
    }

    /**
     * 管理角色的题目阅读能力不能变成他人原题及隐藏数据管理能力
     */
    @Test
    void readerAndContestManagerCannotManageForeignOriginal() {
        login(9L, "CONTEST_MANAGER");
        assertDoesNotThrow(() -> access.requireReadProblem(problem));
        assertThrows(ProblemPermissionException.class, () -> access.requireManageProblem(problem));
        assertFalse(access.canManageContest(contest));
        problem.setAuthorId(9L);
        assertTrue(access.canManageProblem(problem));
        login(9L, "USER");
        assertFalse(access.canManageProblem(problem));
    }

    /**
     * 即使具备另一场比赛的资格，也不能将他人的活动专属题重新包装进自己的比赛
     */
    @Test
    void selectingForeignExclusiveProblemsUsesOneBatchCheck() {
        login(9L, "CONTEST_MANAGER");
        when(problems.hasActiveContestReferences(eq(List.of(10L)), any())).thenReturn(true);
        assertThrows(ProblemPermissionException.class, () -> access.requireUseProblems(List.of(problem)));
        verify(problems, never()).hasActiveContestReference(any(), any());
        problem.setAuthorId(9L);
        assertDoesNotThrow(() -> access.requireUseProblems(List.of(problem)));
        verify(problems, times(1)).hasActiveContestReferences(anyList(), any());
    }

    /**
     * 真正私有比赛不会接受历史密码报名资格，创建者撤权后只能读取
     */
    @Test
    void privateContestIgnoresOldMembershipAndDowngradedOwnerCannotPreview() {
        contest.setVisibility(2);
        login(9L, "CONTEST_MANAGER");
        assertThrows(ContestNotAccessException.class, () -> access.requireContestAccess(contest));
        verifyNoInteractions(users);
        login(8L, "USER");
        assertTrue(access.canAccessContest(contest));
        assertFalse(access.canManageContest(contest));
        contest.setStartTime(LocalDateTime.now().plusHours(1));
        assertThrows(ContestNotStartException.class, () -> access.requireContestContent(contest));
    }

    /**
     * 密码资格在比赛结束后继续有效
     * 正确资格不能让未开始比赛开放题面
     */
    @Test
    @SuppressWarnings("unchecked")
    void passwordQualificationAllowsReviewButNotEarlyContent() {
        login(9L, "PROBLEM_SETTER");
        contest.setVisibility(1);
        LambdaQueryChainWrapper<ContestUser> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.exists()).thenReturn(true);
        when(users.lambdaQuery()).thenReturn(query);
        contest.setEndTime(LocalDateTime.now().minusSeconds(1));
        assertDoesNotThrow(() -> access.requireContestContent(contest));
        contest.setStartTime(LocalDateTime.now().plusHours(1));
        assertThrows(ContestNotStartException.class, () -> access.requireContestContent(contest));
    }

    /**
     * 比赛代码分享不绕过赛中限制，赛后也不自动开放未分享代码
     */
    @Test
    void contestCodeRequiresStageResourceAndSharingChecks() {
        login(9L, "USER");
        Judge judge = new Judge();
        judge.setContestId(20L);
        judge.setProblemId(10L);
        judge.setUserId(7L);
        judge.setShare(true);
        assertThrows(SubmissionException.class, () -> access.canReadSubmissionCode(judge));
        contest.setEndTime(LocalDateTime.now().minusSeconds(1));
        assertTrue(access.canReadSubmissionCode(judge));
        judge.setShare(false);
        assertFalse(access.canReadSubmissionCode(judge));
        login(8L, "CONTEST_MANAGER");
        assertTrue(access.canReadSubmissionCode(judge));
        contest.setVisibility(2);
        login(9L, "ADMIN");
        assertTrue(access.canReadSubmissionCode(judge));
    }

    /**
     * 匿名列表生成公开范围，不依赖需要认证的用户 ID 查询
     */
    @Test
    void anonymousScopeContainsNoManagementPrivileges() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertNull(access.problemScope().getUserId());
        assertFalse(access.problemScope().isAdministrator());
        assertFalse(access.problemScope().isPrivateAccess());
    }

    /**
     * 尚未认证的用户令牌不能为公开列表取得管理范围
     */
    @Test
    void unauthenticatedPrincipalHasNoManagementScope() {
        login(1L, "ADMIN");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        authentication.setAuthenticated(false);
        assertNull(access.problemScope().getUserId());
        assertFalse(access.problemScope().isAdministrator());
        assertFalse(access.problemScope().isPrivateAccess());
    }

    /**
     * 管理身份也不能在比赛时段外创建正式比赛提交
     * @param id 当前用户 ID
     * @param role 当前角色
     */
    @ParameterizedTest
    @CsvSource({"1, USER", "9, ADMIN", "8, CONTEST_MANAGER", "9, PROBLEM_SETTER", "9, USER"})
    @SuppressWarnings("unchecked")
    void formalContestSubmissionAlwaysRequiresActiveWindow(long id, String role) {
        login(id, role);
        JudgeServiceImpl service = new JudgeServiceImpl();
        ReflectionTestUtils.setField(service, "resourceAccess", access);
        ReflectionTestUtils.setField(service, "contests", contests);
        ReflectionTestUtils.setField(service, "problems", problems);
        LambdaQueryChainWrapper<Contest> contestQuery = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(contestQuery.eq(any(), any())).thenReturn(contestQuery);
        when(contestQuery.last("FOR UPDATE")).thenReturn(contestQuery);
        when(contestQuery.one()).thenReturn(contest);
        when(contests.lambdaQuery()).thenReturn(contestQuery);
        JudgeDTO request = new JudgeDTO();
        request.setIsContest(true);
        request.setContestId(20L);
        request.setProblemId("A");
        contest.setStartTime(LocalDateTime.now().plusHours(1));
        assertThrows(ContestNotStartException.class, () -> service.submitJudge(request));
        contest.setStartTime(LocalDateTime.now().minusHours(2));
        contest.setEndTime(LocalDateTime.now().minusHours(1));
        assertThrows(ContestException.class, () -> service.submitJudge(request));
        verify(problems, never()).lambdaQuery();
    }

    /**
     * 比赛管理员能重评本人比赛的他人原题提交，不能重评其他比赛
     */
    @Test
    @SuppressWarnings("unchecked")
    void contestManagerRejudgeUsesContestOwnershipNotProblemOwnership() {
        login(8L, "CONTEST_MANAGER");
        problem.setAuthorId(7L);
        JudgeServiceImpl service = new JudgeServiceImpl();
        JudgeEntityService judges = mock(JudgeEntityService.class);
        ReflectionTestUtils.setField(service, "resourceAccess", access);
        ReflectionTestUtils.setField(service, "contests", contests);
        ReflectionTestUtils.setField(service, "problems", problems);
        ReflectionTestUtils.setField(service, "judges", judges);
        Judge submission = new Judge();
        submission.setContestId(20L);
        submission.setProblemId(10L);
        when(judges.getById(30L)).thenReturn(submission);
        LambdaQueryChainWrapper<Contest> contestQuery = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(contestQuery.eq(any(), any())).thenReturn(contestQuery);
        when(contestQuery.last("FOR UPDATE")).thenReturn(contestQuery);
        when(contestQuery.one()).thenReturn(contest);
        when(contests.lambdaQuery()).thenReturn(contestQuery);
        LambdaQueryChainWrapper<Problem> problemQuery = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(problemQuery.eq(any(), any())).thenReturn(problemQuery);
        when(problemQuery.last("FOR UPDATE")).thenReturn(problemQuery);
        when(problems.lambdaQuery()).thenReturn(problemQuery);
        // 通过归属授权后到达实际重评的原题锁定步骤
        assertThrows(ProblemNotExistException.class, () -> service.rejudge(30L, null));
        verify(problemQuery).eq(any(), eq(10L));
        verify(problemQuery).one();
        contest.setCreatorId(9L);
        assertThrows(SubmissionException.class, () -> service.rejudge(30L, null));
        verify(problemQuery, times(1)).one();
    }

    /**
     * 设置真实的有效登录上下文
     * @param id 当前用户 ID
     * @param role 当前角色编码
     */
    private void login(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setStatus(UserStatus.OK);
        LoginUser principal = new LoginUser(user, List.of(role), List.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
