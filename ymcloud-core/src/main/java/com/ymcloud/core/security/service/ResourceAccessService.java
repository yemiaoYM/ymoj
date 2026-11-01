package com.ymcloud.core.security.service;

import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.contest.ContestNotAccessException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.exception.contest.ContestNotStartException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.exception.submission.SubmissionException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.bo.ProblemAccessScope;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.RoleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 统一题目归属、比赛资格与生命周期授权，不替代 Controller 的功能权限检查
 */
@Service
public class ResourceAccessService {

    @Autowired
    private ProblemEntityService problems;

    @Autowired
    private ContestUserEntityService contestUsers;

    @Autowired
    private ContestEntityService contests;

    /**
     * 判断当前用户是否具有出题能力
     * @return 是否可创建题目及管理本人原题
     */
    public boolean isProblemManager() {
        LoginUser user = SecurityUtils.getLoginUser();
        return SecurityUtils.isAdmin()
                || user.getRoles().contains(RoleType.PROBLEM_SETTER.getCode())
                || user.getRoles().contains(RoleType.CONTEST_MANAGER.getCode());
    }

    /**
     * 判断当前用户是否具有比赛创建能力
     * @return 是否可创建比赛
     */
    public boolean isContestManager() {
        return SecurityUtils.isAdmin() || SecurityUtils.getLoginUser().getRoles().contains(RoleType.CONTEST_MANAGER.getCode());
    }

    /**
     * 拒绝不具备出题角色的后台调用
     */
    public void requireProblemManager() {
        if (!isProblemManager()) {
            throw new ProblemPermissionException("当前角色没有题目管理权限");
        }
    }

    /**
     * 拒绝不具备比赛管理角色的后台调用
     */
    public void requireContestManager() {
        if (!isContestManager()) {
            throw new ContestNotAccessException("当前角色没有比赛管理权限");
        }
    }

    /**
     * 判断题目是否处于当前用户的管理范围
     * @param problem 已加载的题目
     * @return 是否可以管理原题及隐藏测试数据
     */
    public boolean canManageProblem(Problem problem) {
        return SecurityUtils.isAdmin() || (isProblemManager()
                && Objects.equals(problem.getAuthorId(), SecurityUtils.getUserId()));
    }

    /**
     * 校验原题及隐藏测试数据的管理归属
     * @param problem 已加载的题目
     */
    public void requireManageProblem(Problem problem) {
        if (!canManageProblem(problem)) {
            throw new ProblemPermissionException("只能管理本人创建的题目");
        }
    }

    /**
     * 判断比赛是否处于当前用户的管理范围
     * @param contest 已加载的比赛
     * @return 是否可以管理比赛
     */
    public boolean canManageContest(Contest contest) {
        return SecurityUtils.isAdmin() || (isContestManager() && Objects.equals(contest.getCreatorId(), SecurityUtils.getUserId()));
    }

    /**
     * 校验比赛管理范围，创建者已撤权时同样拒绝管理操作
     * @param contest 已加载的比赛
     */
    public void requireManageContest(Contest contest) {
        if (!canManageContest(contest)) {
            throw new ContestNotAccessException("只能管理本人创建的比赛");
        }
    }

    /**
     * 根据尚未结束的非克隆比赛计算实时状态，不回写题目记录
     * 无有效引用时将已存的比赛专属标记按私有处理
     * @param problem 已加载的题目
     * @return 公开、私有或比赛专属状态
     */
    public int effectiveVisibility(Problem problem) {
        if (problems.hasActiveContestReference(problem.getId(), LocalDateTime.now())) {
            return ProblemConstants.CONTEST_PROBLEM;
        }
        return Objects.equals(problem.getVisibility(), ProblemConstants.CONTEST_PROBLEM)
                ? ProblemConstants.PRIVATE : problem.getVisibility();
    }

    /**
     * 校验普通题库详情、练习提交及非比赛提交记录的访问权限
     * @param problem 已加载的题目
     */
    public void requireReadProblem(Problem problem) {
        if (canManageProblem(problem)) {
            return;
        }
        int visibility = effectiveVisibility(problem);
        if (visibility == ProblemConstants.CONTEST_PROBLEM) {
            throw new ProblemPermissionException("比赛专属题目，请通过对应比赛访问");
        }
        if (visibility != ProblemConstants.PUBLIC && !isProblemManager()) {
            throw new ProblemPermissionException();
        }
    }

    /**
     * 校验比赛选题，禁止借另一场比赛绕过他人专属题限制
     * @param selected 已加载并锁定的候选原题
     */
    public void requireUseProblems(List<Problem> selected) {
        requireProblemManager();
        if (SecurityUtils.isAdmin()) {
            return;
        }
        List<Long> foreignIds = selected.stream()
                .filter(problem -> !Objects.equals(problem.getAuthorId(), SecurityUtils.getUserId()))
                .map(Problem::getId)
                .toList();
        if (!foreignIds.isEmpty() && problems.hasActiveContestReferences(foreignIds, LocalDateTime.now())) {
            throw new ProblemPermissionException("他人比赛专属题目解除专属状态后才能加入比赛");
        }
    }

    /**
     * 在公开查询入口区分登录与匿名身份，生成数据库分页前使用的授权范围
     * @return 当前用户的题目查询范围及统一查询时间
     */
    public ProblemAccessScope problemScope() {
        ProblemAccessScope scope = new ProblemAccessScope();
        if (new AuthenticationTrustResolverImpl().isAuthenticated(SecurityUtils.getAuthentication())) {
            scope.setUserId(SecurityUtils.getUserId());
            scope.setAdministrator(SecurityUtils.isAdmin());
            scope.setPrivateAccess(isProblemManager());
        }
        scope.setNow(LocalDateTime.now());
        return scope;
    }

    /**
     * 判断比赛入口访问资格
     * 创建者保留本人私有比赛的读取资格
     * @param contest 已加载的比赛
     * @return 是否有资格进入比赛
     */
    public boolean canAccessContest(Contest contest) {
        if (Objects.equals(contest.getVisibility(), ContestConstants.VISIBILITY_PUBLIC) || SecurityUtils.isAdmin()
                || Objects.equals(contest.getCreatorId(), SecurityUtils.getUserId())) {
            return true;
        }
        return Objects.equals(contest.getVisibility(), ContestConstants.VISIBILITY_PASSWORD)
                && contestUsers.lambdaQuery()
                .eq(ContestUser::getContestId, contest.getId())
                .eq(ContestUser::getUserId, SecurityUtils.getUserId())
                .exists();
    }

    /**
     * 校验比赛入口资格，私有比赛不接受历史报名记录作为授权
     * @param contest 已加载的比赛
     */
    public void requireContestAccess(Contest contest) {
        if (!canAccessContest(contest)) {
            throw new ContestNotAccessException(Objects.equals(contest.getVisibility(), ContestConstants.VISIBILITY_PASSWORD)
                    ? "请先到比赛首页输入密码" : "无权访问该私有比赛");
        }
    }

    /**
     * 校验比赛题目及记录的访问资格和开放阶段，管理者可赛前预览
     * @param contest 已加载的比赛
     */
    public void requireContestContent(Contest contest) {
        requireContestAccess(contest);
        if (LocalDateTime.now().isBefore(contest.getStartTime()) && !canManageContest(contest)) {
            throw new ContestNotStartException();
        }
    }

    /**
     * 校验提交所属资源及比赛阶段，同时返回代码读取资格
     * @param submission 已加载的正式提交
     * @return 是否可以查看代码和包含诊断信息的状态
     */
    public boolean canReadSubmissionCode(Judge submission) {
        boolean manager = SecurityUtils.isAdmin();
        boolean owner = Objects.equals(submission.getUserId(), SecurityUtils.getUserId());
        if (submission.getContestId() == JudgeConstants.PRACTICE_CONTEST_ID) {
            Problem problem = problems.getById(submission.getProblemId());
            if (problem == null) {
                throw new ProblemNotExistException();
            }
            requireReadProblem(problem);
        } else {
            Contest contest = contests.getById(submission.getContestId());
            if (contest == null) {
                throw new ContestNotExistException();
            }
            requireContestContent(contest);
            manager = canManageContest(contest);
            if (!manager && !owner && LocalDateTime.now().isBefore(contest.getEndTime())) {
                throw new SubmissionException("比赛结束前禁止查看他人提交详情", HttpStatus.FORBIDDEN);
            }
        }
        return manager || owner || Boolean.TRUE.equals(submission.getShare());
    }

}
