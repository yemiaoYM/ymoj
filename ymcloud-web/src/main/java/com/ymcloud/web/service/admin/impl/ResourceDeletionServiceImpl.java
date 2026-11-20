package com.ymcloud.web.service.admin.impl;

import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.core.web.dao.ProblemCaseEntityService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.service.admin.ResourceDeletionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 按比赛、题目、任务、提交的顺序加锁并删除业务记录，保留文件登记和实际文件
 */
@Service
public class ResourceDeletionServiceImpl implements ResourceDeletionService {

    private static final Set<JudgeStatus> UNFINISHED = Set.of(JudgeStatus.STATUS_PENDING,
            JudgeStatus.STATUS_COMPILING,
            JudgeStatus.STATUS_JUDGING,
            JudgeStatus.STATUS_SUBMITTING,
            JudgeStatus.STATUS_NOT_SUBMITTED,
            JudgeStatus.STATUS_NULL);

    @Autowired
    private ResourceAccessService access;

    @Autowired
    private ProblemEntityService problems;

    @Autowired
    private ContestEntityService contests;

    @Autowired
    private ContestProblemEntityService contestProblems;

    @Autowired
    private ContestUserEntityService contestUsers;

    @Autowired
    private ProblemTagEntityService problemTags;

    @Autowired
    private JudgeEntityService judges;

    @Autowired
    private JudgeDispatchEntityService dispatches;

    @Autowired
    private JudgeCaseEntityService cases;

    @Autowired
    private ProblemCaseEntityService problemCases;

    @Autowired
    private JudgeStatusCache cache;

    /**
     * 锁定题目，拒绝任何比赛引用及未完成任务，删除专属业务记录并保留文件登记
     * 保留历史 AC 记录，不因题目删除扣减用户成绩
     * @param ids 待删除题目 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void deleteProblems(List<Long> ids) {
        validateIds(ids);
        List<Problem> selected = problems.lambdaQuery()
                .in(Problem::getId, ids)
                .orderByAsc(Problem::getId)
                .last("FOR UPDATE")
                .list();
        if (selected.size() != ids.size()) {
            throw new ServiceException("部分题目不存在或已删除");
        }
        selected.forEach(access::requireManageProblem);
        if (contestProblems.lambdaQuery()
                .in(ContestProblem::getProblemId, ids)
                .exists()) {
            throw new ServiceException("题目仍被比赛引用，请先解除全部引用，包括已结束及克隆比赛", HttpStatus.CONFLICT);
        }
        List<JudgeDispatch> tasks = dispatches.lambdaQuery()
                .select(JudgeDispatch::getId, JudgeDispatch::getDispatchState)
                .in(JudgeDispatch::getProblemId, ids)
                .orderByAsc(JudgeDispatch::getId)
                .last("FOR UPDATE")
                .list();
        List<Judge> submissions = judges.lambdaQuery()
                .select(Judge::getId, Judge::getStatus, Judge::getExecutionId)
                .in(Judge::getProblemId, ids)
                .orderByAsc(Judge::getId)
                .last("FOR UPDATE")
                .list();
        deleteSubmissions(tasks, submissions);
        problemCases.removeByIds(ids);
        problemTags.lambdaUpdate()
                .in(ProblemTag::getProblemId, ids)
                .remove();
        if (!problems.removeByIds(ids)) {
            throw new ServiceException("题目删除失败，操作已回滚");
        }
        cache.evictAfterCommit(submissions);
    }

    /**
     * 锁定比赛及关联原题后删除比赛专属业务记录，保留文件登记
     * 按剩余引用释放非克隆比赛专属状态
     * @param ids 待删除比赛 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void deleteContests(List<Long> ids) {
        validateIds(ids);
        List<Contest> selected = contests.lambdaQuery()
                .in(Contest::getId, ids)
                .orderByAsc(Contest::getId)
                .last("FOR UPDATE")
                .list();
        if (selected.size() != ids.size()) {
            throw new ServiceException("部分比赛不存在或已删除");
        }
        LocalDateTime now = LocalDateTime.now();
        for (Contest contest : selected) {
            access.requireManageContest(contest);
            if (!now.isBefore(contest.getStartTime()) && now.isBefore(contest.getEndTime())) {
                throw new ServiceException("不能删除进行中的比赛", HttpStatus.CONFLICT);
            }
        }
        List<ContestProblem> associations = contestProblems.lambdaQuery()
                .in(ContestProblem::getContestId, ids)
                .list();
        List<Long> problemIds = associations.stream().map(ContestProblem::getProblemId).distinct().toList();
        if (!problemIds.isEmpty()) {
            problems.lambdaQuery()
                    .in(Problem::getId, problemIds)
                    .orderByAsc(Problem::getId)
                    .last("FOR UPDATE")
                    .list();
        }
        List<Judge> candidates = judges.lambdaQuery()
                .select(Judge::getId)
                .in(Judge::getContestId, ids)
                .list();
        List<Long> submissionIds = candidates.stream().map(Judge::getId).toList();
        List<Judge> submissions = List.of();
        if (!submissionIds.isEmpty()) {
            List<JudgeDispatch> tasks = dispatches.lambdaQuery()
                    .select(JudgeDispatch::getId, JudgeDispatch::getDispatchState)
                    .in(JudgeDispatch::getSubmissionId, submissionIds)
                    .orderByAsc(JudgeDispatch::getId)
                    .last("FOR UPDATE")
                    .list();
            submissions = judges.lambdaQuery()
                    .select(Judge::getId, Judge::getStatus, Judge::getExecutionId)
                    .in(Judge::getId, submissionIds)
                    .orderByAsc(Judge::getId)
                    .last("FOR UPDATE")
                    .list();
            deleteSubmissions(tasks, submissions);
        }
        contestUsers.lambdaUpdate()
                .in(ContestUser::getContestId, ids)
                .remove();
        contestProblems.lambdaUpdate()
                .in(ContestProblem::getContestId, ids)
                .remove();
        if (!contests.removeByIds(ids)) {
            throw new ServiceException("比赛删除失败，操作已回滚");
        }
        Set<Long> originalContests = new HashSet<>(selected.stream()
                .filter(contest -> contest.getSourceId() == ContestConstants.ORIGINAL_SOURCE_ID).map(Contest::getId).toList());
        Set<Long> releasable = new HashSet<>(associations.stream()
                .filter(link -> originalContests.contains(link.getContestId()))
                .map(ContestProblem::getProblemId).toList());
        if (!releasable.isEmpty()) {
            problems.releaseContestProblems(List.copyOf(releasable), now);
        }
        cache.evictAfterCommit(submissions);
    }

    /**
     * 拒绝在途任务后删除任务、评测明细和提交，保留历史 AC 记录
     * 调用方已锁定资源、任务和提交
     * @param tasks 已锁定任务
     * @param submissions 已锁定提交
     */
    private void deleteSubmissions(List<JudgeDispatch> tasks, List<Judge> submissions) {
        if (tasks.stream().anyMatch(task -> task.getDispatchState() != JudgeDispatchState.COMPLETED)
                || submissions.stream().anyMatch(judge -> UNFINISHED.contains(judge.getStatus()))) {
            throw new ServiceException("存在未完成评测，请等待终态后重试删除", HttpStatus.CONFLICT);
        }
        if (!tasks.isEmpty()) {
            dispatches.removeByIds(tasks.stream().map(JudgeDispatch::getId).toList());
        }
        if (!submissions.isEmpty()) {
            List<Long> ids = submissions.stream().map(Judge::getId).toList();
            cases.lambdaUpdate()
                    .in(JudgeCase::getJudgeId, ids)
                    .remove();
            judges.removeByIds(ids);
        }
    }

    /**
     * 校验批量删除入口的资源 ID，拒绝重复导致的数量歧义
     * @param ids 待删除 ID
     */
    private void validateIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new ServiceException("删除资源 ID 列表无效", HttpStatus.BAD_REQUEST);
        }
    }
}
