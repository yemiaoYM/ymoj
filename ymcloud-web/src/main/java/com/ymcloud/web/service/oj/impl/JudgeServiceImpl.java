package com.ymcloud.web.service.oj.impl;

import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotAccessException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.exception.contest.ContestNotStartException;
import com.ymcloud.common.exception.contest.ContestProblemNotExistException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.pojo.contract.judge.JudgeLanguage;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.enums.ProblemSource;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeResultService;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.service.oj.JudgeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 提交权限与可靠任务箱归 Web，任务快照中不携带业务实体或对象存储凭据。 */
@Service
public class JudgeServiceImpl implements JudgeService {

    private static final int MAX_CODE_LENGTH = 256 * 1024;
    private static final int MAX_SELF_TEST_LENGTH = 1024 * 1024;
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("Java", "C", "C++", "Python");

    private final JudgeEntityService judges;
    private final JudgeDispatchEntityService dispatches;
    private final ProblemEntityService problems;
    private final LanguageEntityService languages;
    private final ContestEntityService contests;
    private final ContestProblemEntityService contestProblems;
    private final ContestUserEntityService contestUsers;
    private final TestDataService testData;
    private final TestDataVersionEntityService versions;
    private final JudgeDispatchProperties properties;
    private final JudgeMessageCodec codec;
    private final JudgeStatusCache cache;
    private final JudgeResultService results;
    private final JudgeDispatchIdAllocator allocator;

    public JudgeServiceImpl(JudgeEntityService judges, JudgeDispatchEntityService dispatches,
                            ProblemEntityService problems, LanguageEntityService languages,
                            ContestEntityService contests, ContestProblemEntityService contestProblems,
                            ContestUserEntityService contestUsers, TestDataService testData,
                            TestDataVersionEntityService versions, JudgeDispatchProperties properties,
                            JudgeMessageCodec codec, JudgeStatusCache cache, JudgeResultService results,
                            JudgeDispatchIdAllocator allocator) {
        this.judges = judges;
        this.dispatches = dispatches;
        this.problems = problems;
        this.languages = languages;
        this.contests = contests;
        this.contestProblems = contestProblems;
        this.contestUsers = contestUsers;
        this.testData = testData;
        this.versions = versions;
        this.properties = properties;
        this.codec = codec;
        this.cache = cache;
        this.results = results;
        this.allocator = allocator;
    }

    /** 版本读取持有题目行锁，提交记录和固定任务快照在同一事务提交。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitJudge(JudgeDTO request) {
        if (Boolean.TRUE.equals(request.getIsContest())) {
            return submitContest(request);
        }
        User user = SecurityUtils.getUser();
        Problem problem = problems.lambdaQuery()
                .eq(Problem::getProblemId, request.getProblemId())
                .one();
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        if (Objects.equals(problem.getVisibility(), ProblemConstants.CONTEST_PROBLEM)) {
            throw new JudgeException("请从比赛官网进行提交评测");
        }
        if (Objects.equals(problem.getVisibility(), ProblemConstants.PRIVATE)
                && !Objects.equals(user.getId(), problem.getAuthorId())) {
            throw new ProblemPermissionException();
        }
        return createSubmission(request, user, problem, null, null);
    }

    private Long submitContest(JudgeDTO request) {
        if (request.getContestId() == null) {
            throw new ContestNotExistException();
        }
        Contest contest = contests.lambdaQuery()
                .eq(Contest::getId, request.getContestId())
                .one();
        if (contest == null) {
            throw new ContestNotExistException();
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            throw new ContestNotStartException();
        }
        if (now.isAfter(contest.getEndTime()) && !Boolean.TRUE.equals(contest.getAllowEndSubmit())) {
            throw new ContestException("比赛结束后禁止提交");
        }
        User user = SecurityUtils.getUser();
        if (!Objects.equals(contest.getVisibility(), ContestConstants.VISIBILITY_PUBLIC)) {
            boolean access = contestUsers.lambdaQuery()
                    .eq(ContestUser::getContestId, contest.getId())
                    .eq(ContestUser::getUserId, user.getId())
                    .exists();
            if (!access) {
                throw new ContestNotAccessException();
            }
        }
        ContestProblem contestProblem = contestProblems.lambdaQuery()
                .eq(ContestProblem::getContestId, contest.getId())
                .eq(ContestProblem::getDisplayId, request.getProblemId())
                .one();
        if (contestProblem == null) {
            throw new ContestProblemNotExistException();
        }
        Problem problem = problems.lambdaQuery()
                .eq(Problem::getId, contestProblem.getProblemId())
                .one();
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        return createSubmission(request, user, problem, contest, contestProblem);
    }

    private Long createSubmission(JudgeDTO request, User user, Problem problem,
                                  Contest contest, ContestProblem contestProblem) {
        Language language = requireLanguage(request.getLanguage(), problem.getSource().getCode());
        validateResources(problem.getTimeLimit(), problem.getMemoryLimit());
        Long versionId = testData.getCurrentVersion(problem.getId());
        if (versionId == null || versionId <= 0) {
            throw new JudgeException("题目暂无可用的测试数据版本");
        }
        TestDataVersion version = versions.lambdaQuery()
                .eq(TestDataVersion::getId, versionId)
                .eq(TestDataVersion::getProblemId, problem.getId())
                .one();
        if (version == null || version.getManifestSha256() == null
                || !version.getManifestSha256().matches("[0-9a-f]{64}")) {
            throw new JudgeException("题目测试数据版本清单无效");
        }
        String executionId = UUID.randomUUID().toString();
        JudgeTask task = newTask(executionId, language, request.getCode());
        task.setStrategy(JudgeDispatchState.FORMAL);
        task.setProblemId(problem.getId());
        task.setTestDataVersionId(versionId);
        task.setManifestSha256(version.getManifestSha256());
        task.setTimeLimit(problem.getTimeLimit());
        task.setMemoryLimit(problem.getMemoryLimit());
        task.setMode(contest == null ? problem.getMode() : contest.getMode());
        enqueue(task, user.getId());
        Judge judge = Judge.builder()
                .id(task.getSubmissionId())
                .problemId(problem.getId())
                .testDataVersionId(versionId)
                .executionId(executionId)
                .problemTitle(contestProblem == null ? problem.getTitle() : contestProblem.getDisplayTitle())
                .displayId(contestProblem == null ? problem.getProblemId() : contestProblem.getDisplayId())
                .userId(user.getId())
                .username(user.getUsername())
                .language(language.getName())
                .status(JudgeStatus.STATUS_PENDING)
                .code(request.getCode())
                .codeLength(request.getCode().length())
                .contestId(contest == null ? 0L : contest.getId())
                .contestProblemId(contestProblem == null ? 0L : contestProblem.getId())
                .judgeServer(JudgeConstants.LOCAL_JUDGE_SERVER)
                .ip(IpUtils.getIpAddr())
                .build();
        if (!judges.save(judge)) {
            throw new JudgeException("提交失败，请稍后再试");
        }
        return judge.getId();
    }

    /** 自测有独立的持久任务和用户关联，Web 重启或 Redis 丢失后仍可查询。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long testJudge(TestJudgeDTO request) {
        User user = SecurityUtils.getUser();
        Language language = requireLanguage(request.getLanguage(), ProblemSource.LOCAL.getCode());
        JudgeTask task = newTask(UUID.randomUUID().toString(), language, request.getCode());
        task.setStrategy(JudgeDispatchState.SELF_TEST);
        task.setInput(request.getInput() == null ? "" : request.getInput());
        task.setExpectedOutput(request.getExpectedOutput() == null ? "" : request.getExpectedOutput());
        if (task.getInput().length() > MAX_SELF_TEST_LENGTH
                || task.getExpectedOutput().length() > MAX_SELF_TEST_LENGTH) {
            throw new JudgeException("自测输入或期望输出超过允许的大小");
        }
        task.setTimeLimit(1000L);
        task.setMemoryLimit(256 * 1024L);
        task.setMode(JudgeConstants.ACM);
        enqueue(task, user.getId());
        return task.getSubmissionId();
    }

    private Language requireLanguage(String name, String source) {
        Language language = languages.lambdaQuery()
                .eq(Language::getName, name)
                .eq(Language::getOj, source)
                .eq(Language::getStatus, LanguageConstants.ABLE)
                .one();
        if (language == null) {
            throw new JudgeException("该题目不支持此编程语言");
        }
        return language;
    }

    private JudgeTask newTask(String executionId, Language language, String code) {
        if (code == null || code.isBlank() || code.length() > MAX_CODE_LENGTH) {
            throw new JudgeException("代码为空或超过允许的大小");
        }
        validateLanguage(language);
        Instant now = Instant.now();
        JudgeTask task = new JudgeTask();
        task.setSchemaVersion(JudgeProtocol.SCHEMA_VERSION);
        task.setExecutionId(executionId);
        task.setSubmittedAtEpochMs(now.toEpochMilli());
        task.setDeadlineEpochMs(now.plus(properties.getTotalTimeout()).toEpochMilli());
        task.setCode(code);
        task.setLanguageConfig(languageSnapshot(language));
        return task;
    }

    private void validateLanguage(Language language) {
        boolean supported = language.getName() != null
                && SUPPORTED_LANGUAGES.contains(language.getName());
        boolean compiled = Objects.equals(language.getType(), 0);
        boolean interpreted = Objects.equals(language.getType(), 1);
        boolean missingCompiler = compiled
                && (language.getCompileCmd() == null || language.getCompileCmd().isBlank());
        if (!supported
                || language.getEnv() == null || !properties.getAllowedImages().contains(language.getEnv())
                || language.getFileName() == null || !language.getFileName().matches("[A-Za-z0-9_-]+\\.(java|c|cpp|py)")
                || language.getRunCmd() == null || language.getRunCmd().isBlank()
                || (!compiled && !interpreted) || missingCompiler) {
            throw new JudgeException("编程语言配置不完整或不在允许的判题环境中，请联系管理员");
        }
    }

    private JudgeLanguage languageSnapshot(Language language) {
        JudgeLanguage snapshot = new JudgeLanguage();
        snapshot.setName(language.getName());
        snapshot.setImage(language.getImage());
        snapshot.setEnv(language.getEnv());
        snapshot.setFileName(language.getFileName());
        snapshot.setCompileCmd(language.getCompileCmd());
        snapshot.setRunCmd(language.getRunCmd());
        snapshot.setType(language.getType());
        snapshot.setVersion(language.getVersion());
        snapshot.setContentType(language.getContentType());
        snapshot.setFileExtension(language.getFileExtension());
        return snapshot;
    }

    private void enqueue(JudgeTask task, Long userId) {
        if (!Objects.equals(task.getMode(), JudgeConstants.ACM)
                && !Objects.equals(task.getMode(), JudgeConstants.OI)) {
            throw new JudgeException("题目或比赛的评测模式配置无效");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        JudgeDispatch dispatch = new JudgeDispatch();
        dispatch.setExecutionId(task.getExecutionId());
        dispatch.setUserId(userId);
        dispatch.setStrategy(task.getStrategy());
        dispatch.setProblemId(task.getProblemId());
        dispatch.setTestDataVersionId(task.getTestDataVersionId());
        dispatch.setTaskPayload("");
        dispatch.setDispatchState(JudgeDispatchState.READY);
        dispatch.setStatus(JudgeStatus.STATUS_PENDING);
        dispatch.setLastResultSequence(0);
        dispatch.setPublishAttempts(0);
        dispatch.setRecoveryAttempts(0);
        dispatch.setNextAttemptTime(now);
        dispatch.setDeadlineTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(task.getDeadlineEpochMs()), ZoneOffset.UTC));
        Long submissionId = allocator.reserve(dispatch);
        task.setSubmissionId(submissionId);
        dispatch.setSubmissionId(submissionId);
        dispatch.setTaskPayload(codec.write(task));
        boolean completed = dispatches.lambdaUpdate()
                .eq(JudgeDispatch::getId, dispatch.getId())
                .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                .isNull(JudgeDispatch::getSubmissionId)
                .set(JudgeDispatch::getSubmissionId, submissionId)
                .set(JudgeDispatch::getTaskPayload, dispatch.getTaskPayload())
                .update();
        if (!completed) {
            throw new JudgeException("任务快照保存失败，请稍后再试");
        }
        cache.putAfterCommit(task.getSubmissionId(), task.getExecutionId(), results.toView(dispatch));
    }

    @Override
    public JudgeResultVO getStatus(Long submissionId) {
        Long userId = SecurityUtils.getUserId();
        JudgeDispatch dispatch = dispatches.lambdaQuery()
                .select(JudgeDispatch::getId, JudgeDispatch::getSubmissionId, JudgeDispatch::getExecutionId,
                        JudgeDispatch::getUserId, JudgeDispatch::getStrategy, JudgeDispatch::getStatus)
                .eq(JudgeDispatch::getSubmissionId, submissionId)
                .one();
        if (dispatch == null) {
            return historicalStatus(submissionId, userId);
        }
        requireOwner(userId, dispatch.getUserId());
        JudgeResultVO cached = cache.get(submissionId, dispatch.getExecutionId());
        if (cached != null && cached.getStatus() == dispatch.getStatus()) {
            return cached;
        }
        JudgeResultVO current;
        if (Objects.equals(dispatch.getStrategy(), JudgeDispatchState.FORMAL)) {
            Judge judge = judges.lambdaQuery()
                    .select(Judge::getStatus, Judge::getTime, Judge::getMemory, Judge::getErrorMessage,
                            Judge::getExecutionId)
                    .eq(Judge::getId, submissionId)
                    .one();
            if (judge == null || !Objects.equals(judge.getExecutionId(), dispatch.getExecutionId())) {
                throw new JudgeException("评测执行信息已更新，请刷新后重试");
            }
            current = JudgeResultVO.builder()
                    .status(judge.getStatus())
                    .time(judge.getTime())
                    .memory(judge.getMemory())
                    .errorMessage(judge.getErrorMessage())
                    .build();
        } else {
            JudgeDispatch durable = dispatches.lambdaQuery()
                    .select(JudgeDispatch::getSubmissionId, JudgeDispatch::getExecutionId,
                            JudgeDispatch::getUserId, JudgeDispatch::getStrategy,
                            JudgeDispatch::getStatus, JudgeDispatch::getTaskPayload,
                            JudgeDispatch::getResultPayload)
                    .eq(JudgeDispatch::getId, dispatch.getId())
                    .one();
            if (durable == null || !Objects.equals(durable.getExecutionId(), dispatch.getExecutionId())) {
                throw new JudgeException("评测执行信息已更新，请刷新后重试");
            }
            requireOwner(userId, durable.getUserId());
            current = results.toView(durable);
        }
        cache.putAfterCommit(submissionId, dispatch.getExecutionId(), current);
        return current;
    }

    private JudgeResultVO historicalStatus(Long submissionId, Long userId) {
        Judge historical = judges.lambdaQuery()
                .select(Judge::getUserId, Judge::getStatus, Judge::getTime,
                        Judge::getMemory, Judge::getErrorMessage)
                .eq(Judge::getId, submissionId)
                .one();
        if (historical == null) {
            throw new JudgeException("评测记录不存在");
        }
        requireOwner(userId, historical.getUserId());
        return JudgeResultVO.builder()
                .status(historical.getStatus())
                .time(historical.getTime())
                .memory(historical.getMemory())
                .errorMessage(historical.getErrorMessage())
                .build();
    }

    private void requireOwner(Long currentUser, Long owner) {
        if (!Objects.equals(currentUser, owner) && !SecurityUtils.isSuperAdmin(currentUser)) {
            throw new JudgeException("无权查看他人的评测状态", HttpStatus.FORBIDDEN);
        }
    }

    private void validateResources(Long time, Long memory) {
        if (time == null || time < 1 || time > 60_000
                || memory == null || memory < 1024 || memory > 2 * 1024 * 1024L) {
            throw new JudgeException("题目资源限制无效：时间须为 1～60000ms，内存须为 1024～2097152KiB");
        }
    }
}
