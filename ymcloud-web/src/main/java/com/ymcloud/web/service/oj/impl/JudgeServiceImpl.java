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
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeResultService;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.service.oj.JudgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 校验本站提交权限并持久化可靠任务快照，不携带业务实体或存储凭据。
 */
@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    private static final int MAX_CODE_LENGTH = 256 * 1024;
    private static final int MAX_SELF_TEST_LENGTH = 1024 * 1024;
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("Java", "C", "C++", "Python");

    @Autowired
    private JudgeEntityService judges;

    @Autowired
    private JudgeDispatchEntityService dispatches;

    @Autowired
    private ProblemEntityService problems;

    @Autowired
    private LanguageEntityService languages;

    @Autowired
    private ContestEntityService contests;

    @Autowired
    private ContestProblemEntityService contestProblems;

    @Autowired
    private ContestUserEntityService contestUsers;

    @Autowired
    private TestDataService testData;

    @Autowired
    private TestDataVersionEntityService versions;

    @Autowired
    private JudgeDispatchProperties properties;

    @Autowired
    private JudgeMessageCodec codec;

    @Autowired
    private JudgeStatusCache cache;

    @Autowired
    private JudgeResultService results;

    @Autowired
    private JudgeDispatchIdAllocator allocator;

    /**
     * 版本读取持有题目行锁，提交记录和固定任务快照在同一事务提交。
     * @param request 经过结构校验的提交请求
     * @return 本次正式提交 ID
     */
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

    /**
     * 检查比赛时段、参赛资格和比赛题目映射后创建提交。
     * @param request 经过结构校验的比赛提交请求
     * @return 本次比赛提交 ID
     */
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

    /**
     * 使用本站语言配置创建正式提交，固定题目测试版本及本次执行身份。
     * @param request 提交请求
     * @param user 当前用户
     * @param problem 已验证访问权限的题目，来源仅表示出处
     * @param contest 所属比赛，普通提交为 null
     * @param contestProblem 比赛题目映射，普通提交为 null
     * @return 提交 ID
     */
    private Long createSubmission(JudgeDTO request, User user, Problem problem,
                                  Contest contest, ContestProblem contestProblem) {
        if (!ProblemConstants.JUDGE_MODE_DEFAULT.equals(problem.getJudgeMode())) {
            throw new JudgeException("该题目的判题方式尚未支持", HttpStatus.BAD_REQUEST);
        }
        Language language = requireLanguage(request.getLanguage());
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
        task.setMode(resolveScoringMode(contest));
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

    /**
     * 普通练习采用 ACM 判定，比赛按赛制选择单次提交的评分算法。
     * OI 与 IOI 都按测试点计分，跨提交的取分规则由比赛排名负责。
     * @param contest 所属比赛，普通练习为 null
     * @return Worker 既有评分协议：0=ACM，1=测试点计分
     * @throws JudgeException 比赛模式缺失或不受支持
     */
    private Integer resolveScoringMode(Contest contest) {
        if (contest == null || Objects.equals(contest.getMode(), ContestConstants.MODE_ACM)) {
            return JudgeConstants.ACM;
        }
        if (Objects.equals(contest.getMode(), ContestConstants.MODE_OI)
                || Objects.equals(contest.getMode(), ContestConstants.MODE_IOI)) {
            return JudgeConstants.OI;
        }
        throw new JudgeException("比赛计分模式配置无效", HttpStatus.BAD_REQUEST);
    }

    /**
     * 创建与用户关联的持久自测任务，Web 重启或 Redis 丢失后仍可查询。
     * @param request 源码、语言、输入及期望输出
     * @return 自测任务的提交 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long testJudge(TestJudgeDTO request) {
        User user = SecurityUtils.getUser();
        Language language = requireLanguage(request.getLanguage());
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

    /**
     * 查询本站启用的语言配置，正式提交和自测共用同一执行平台。
     * @param name 请求使用的语言名称
     * @return 已启用的本站语言配置，执行参数由创建任务时继续校验
     * @throws JudgeException 本站未启用该语言
     */
    private Language requireLanguage(String name) {
        Language language = languages.lambdaQuery()
                .eq(Language::getName, name)
                .eq(Language::getOj, LanguageConstants.LOCAL_PLATFORM)
                .eq(Language::getStatus, LanguageConstants.ABLE)
                .one();
        if (language == null) {
            throw new JudgeException("该题目不支持此编程语言");
        }
        return language;
    }

    /**
     * 校验源码和语言后固定任务身份与总截止时间。
     * @param executionId 本次新执行标识
     * @param language 已启用的本站语言
     * @param code 用户源码，非空且有容量上限
     * @return 尚未分配提交 ID 的任务骨架
     */
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

    /**
     * 限制支持语言、固定文件名和编译类型，历史命令不参与实际执行。
     * @param language 已启用的本站语言配置
     */
    private void validateLanguage(Language language) {
        boolean supported = language.getName() != null
                && SUPPORTED_LANGUAGES.contains(language.getName());
        boolean compiled = Objects.equals(language.getType(), 0);
        boolean interpreted = Objects.equals(language.getType(), 1);
        if (!supported
                || language.getFileName() == null || !language.getFileName().matches("[A-Za-z0-9_-]+\\.(java|c|cpp|py)")
                || (!compiled && !interpreted)) {
            throw new JudgeException("编程语言入口、文件名或类型无效，请联系管理员");
        }
        // image、env 和命令只保留在提交快照中，实际执行由 Worker 的固定语言入口决定。
    }

    /**
     * 复制必要语言元数据到任务契约，不传持久实体。
     * @param language 已验证的本站语言配置
     * @return 不可随后续配置修改而改变的语言快照
     */
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

    /**
     * 在提交事务中分配 ID 并保存完整任务快照，提交后再更新缓存。
     * @param task 已验证的任务，回填提交 ID
     * @param userId 从安全上下文取得的操作者 ID
     */
    private void enqueue(JudgeTask task, Long userId) {
        if (!Objects.equals(task.getMode(), JudgeConstants.ACM)
                && !Objects.equals(task.getMode(), JudgeConstants.OI)) {
            throw new JudgeException("提交计分模式配置无效");
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
        try {
            dispatch.setTaskPayload(codec.write(task));
        } catch (IllegalArgumentException e) {
            throw new JudgeException("评测任务无法发送，代码及自测数据序列化后不得超过 3MiB，请缩小后重试");
        }
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
        logSubmissionAfterCommit(task, userId);
    }

    /**
     * 仅在业务事务提交成功后记录创建成功，避免回滚任务出现在开发日志中。
     * @param task 已持久化任务
     * @param userId 可信操作者 ID
     */
    private void logSubmissionAfterCommit(JudgeTask task, Long userId) {
        Runnable record = () -> log.info("创建判题任务成功，提交编号：{}，执行编号：{}，用户编号：{}，题目编号：{}，数据版本：{}，策略：{}，语言：{}",
                task.getSubmissionId(), task.getExecutionId(), userId, task.getProblemId(),
                task.getTestDataVersionId(), task.getStrategy(), task.getLanguageConfig().getName());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 提交成功后执行任务创建日志回调。
                 */
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    /**
     * 先检查归属再查状态缓存，缓存缺失从持久数据恢复。
     * @param submissionId 提交或自测 ID
     * @return 当前用户有权访问的判题状态
     */
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

    /**
     * 读取任务箱拆分前的提交状态，同样验证查看权限。
     * @param submissionId 历史正式提交 ID
     * @param userId 从安全上下文取得的当前用户 ID
     * @return 允许当前用户查看的历史状态
     */
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

    /**
     * 只允许所属用户或已确认的超级管理员读取私有状态。
     * @param currentUser 从安全上下文取得的当前用户 ID
     * @param owner 任务所属用户 ID
     */
    private void requireOwner(Long currentUser, Long owner) {
        if (!Objects.equals(currentUser, owner) && !SecurityUtils.isSuperAdmin(currentUser)) {
            throw new JudgeException("无权查看他人的评测状态", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * 检查题目时间和内存范围，非法配置不进入消息队列。
     * @param time 时间上限，1～60000 毫秒
     * @param memory 内存上限，1024～2097152 KiB
     */
    private void validateResources(Long time, Long memory) {
        if (time == null || time < 1 || time > 60_000
                || memory == null || memory < 1024 || memory > 2 * 1024 * 1024L) {
            throw new JudgeException("题目资源限制无效：时间须为 1～60000ms，内存须为 1024～2097152KiB");
        }
    }
}
