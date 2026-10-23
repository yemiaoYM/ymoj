package com.ymcloud.web.judge;

import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.entity.UserAcproblem;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-12
 * @Description: 任务、提交、测试点和 AC 记录在同一事务完成；行锁与执行标识阻止重复及过期结果。
 */
@Slf4j
@Service
public class JudgeResultService {

    private static final Set<JudgeVerdict> TERMINAL = Set.of(
            JudgeVerdict.STATUS_CANCELLED, JudgeVerdict.STATUS_PRESENTATION_ERROR,
            JudgeVerdict.STATUS_COMPILE_ERROR, JudgeVerdict.STATUS_WRONG_ANSWER,
            JudgeVerdict.STATUS_ACCEPTED, JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED,
            JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED, JudgeVerdict.STATUS_RUNTIME_ERROR,
            JudgeVerdict.STATUS_SYSTEM_ERROR, JudgeVerdict.STATUS_PARTIAL_ACCEPTED);
    private static final int MAX_CASES = 10_000;

    private final JudgeDispatchEntityService dispatches;
    private final JudgeEntityService judges;
    private final JudgeCaseEntityService cases;
    private final UserEntityService users;
    private final UserAcproblemEntityService accepted;
    private final JudgeMessageCodec codec;
    private final JudgeStatusCache cache;
    private final JudgeDispatchProperties properties;

    public JudgeResultService(JudgeDispatchEntityService dispatches, JudgeEntityService judges,
                              JudgeCaseEntityService cases, UserEntityService users,
                              UserAcproblemEntityService accepted, JudgeMessageCodec codec,
                              JudgeStatusCache cache, JudgeDispatchProperties properties) {
        this.dispatches = dispatches;
        this.judges = judges;
        this.cases = cases;
        this.users = users;
        this.accepted = accepted;
        this.codec = codec;
        this.cache = cache;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public void receive(JudgeResult result) {
        validate(result);
        JudgeDispatch dispatch = lock(result.getSubmissionId());
        if (!matches(dispatch, result)) {
            log.debug("忽略重复、过期或不匹配的判题结果，提交编号：{}，执行编号：{}，数据版本：{}，状态：{}，消息序号：{}",
                    result.getSubmissionId(), result.getExecutionId(), result.getTestDataVersionId(),
                    result.getStatus(), result.getSequence());
            return;
        }
        apply(dispatch, result);
    }

    /** 发布预算耗尽只终止尚未发布的任务，已经收到 Worker 进度的任务继续等待结果。 */
    @Transactional(rollbackFor = Exception.class)
    public void failPublishing(Long submissionId, String executionId, String reason) {
        JudgeDispatch dispatch = lock(submissionId);
        if (dispatch == null || !Objects.equals(dispatch.getExecutionId(), executionId)
                || !Objects.equals(dispatch.getDispatchState(), JudgeDispatchState.READY)) {
            return;
        }
        if (dispatch.getPublishedTime() != null || dispatch.getLastResultSequence() > 0) {
            boolean waiting = dispatches.lambdaUpdate()
                    .eq(JudgeDispatch::getId, dispatch.getId())
                    .eq(JudgeDispatch::getExecutionId, executionId)
                    .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.READY)
                    .set(JudgeDispatch::getDispatchState, JudgeDispatchState.WAITING)
                    .set(JudgeDispatch::getNextAttemptTime, dispatch.getDeadlineTime())
                    .set(JudgeDispatch::getLeaseToken, null)
                    .set(JudgeDispatch::getLeaseUntil, null)
                    .update();
            if (!waiting) {
                throw new JudgeException("评测恢复状态保存失败");
            }
            log.warn("判题发布次数已用尽，保留已发布任务等待结果，提交编号：{}，执行编号：{}，数据版本：{}",
                    submissionId, executionId, dispatch.getTestDataVersionId());
            return;
        }
        log.warn("判题任务无法发布，准备保存系统错误，提交编号：{}，执行编号：{}，数据版本：{}",
                submissionId, executionId, dispatch.getTestDataVersionId());
        apply(dispatch, systemError(dispatch, reason));
    }

    /** 总预算结束后产生最终系统错误，晚到的结果不会覆盖它。 */
    @Transactional(rollbackFor = Exception.class)
    public void expire(Long submissionId, String executionId) {
        JudgeDispatch dispatch = lock(submissionId);
        if (dispatch == null || !Objects.equals(dispatch.getExecutionId(), executionId)
                || Objects.equals(dispatch.getDispatchState(), JudgeDispatchState.COMPLETED)
                || dispatch.getDeadlineTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        log.warn("判题任务超过总截止时间，准备保存系统错误，提交编号：{}，执行编号：{}，数据版本：{}",
                submissionId, executionId, dispatch.getTestDataVersionId());
        apply(dispatch, systemError(dispatch, "评测超时，有限恢复后仍未收到结果，请稍后重新提交"));
    }

    private JudgeDispatch lock(Long submissionId) {
        return dispatches.lambdaQuery()
                .eq(JudgeDispatch::getSubmissionId, submissionId)
                .last("FOR UPDATE")
                .one();
    }

    private boolean matches(JudgeDispatch dispatch, JudgeResult result) {
        return dispatch != null
                && !Objects.equals(dispatch.getDispatchState(), JudgeDispatchState.COMPLETED)
                && sameExecution(dispatch, result)
                && result.getSequence() > dispatch.getLastResultSequence();
    }

    private boolean sameExecution(JudgeDispatch dispatch, JudgeResult result) {
        return Objects.equals(dispatch.getSubmissionId(), result.getSubmissionId())
                && Objects.equals(dispatch.getExecutionId(), result.getExecutionId())
                && Objects.equals(dispatch.getProblemId(), result.getProblemId())
                && Objects.equals(dispatch.getTestDataVersionId(), result.getTestDataVersionId())
                && Objects.equals(dispatch.getStrategy(), result.getStrategy());
    }

    private void apply(JudgeDispatch dispatch, JudgeResult result) {
        Judge judge = null;
        if (Objects.equals(dispatch.getStrategy(), JudgeDispatchState.FORMAL)) {
            judge = judges.lambdaQuery()
                    .eq(Judge::getId, dispatch.getSubmissionId())
                    .last("FOR UPDATE")
                    .one();
            if (judge == null || !Objects.equals(judge.getExecutionId(), result.getExecutionId())
                    || !Objects.equals(judge.getTestDataVersionId(), result.getTestDataVersionId())
                    || !Objects.equals(judge.getProblemId(), result.getProblemId())) {
                log.warn("评测结果与当前提交执行标识不匹配，提交编号：{}，执行编号：{}，数据版本：{}",
                        dispatch.getSubmissionId(), result.getExecutionId(), result.getTestDataVersionId());
                return;
            }
        }
        limitPreviews(result);
        JudgeStatus status = status(result.getStatus());
        int state = result.isTerminal() ? JudgeDispatchState.COMPLETED : JudgeDispatchState.WAITING;
        String payload = codec.write(result);
        if (!saveDispatch(dispatch, result, status, state, payload)) {
            return;
        }
        if (judge != null) {
            saveSubmission(judge, result, status);
        }
        dispatch.setDispatchState(state);
        dispatch.setStatus(status);
        dispatch.setResultPayload(payload);
        dispatch.setLastResultSequence(result.getSequence());
        cache.putAfterCommit(dispatch.getSubmissionId(), dispatch.getExecutionId(), toView(dispatch, result));
        logResultAfterCommit(dispatch, result);
    }

    /** 终态日志只在提交、测试点和 AC 记录全部提交后输出，不记录源码、输入输出或错误正文。 */
    private void logResultAfterCommit(JudgeDispatch dispatch, JudgeResult result) {
        Runnable record = () -> {
            if (result.isTerminal()) {
                log.info("保存判题结果成功，提交编号：{}，执行编号：{}，数据版本：{}，状态：{}，耗时：{}ms，内存：{}KiB，得分：{}",
                        dispatch.getSubmissionId(), dispatch.getExecutionId(), dispatch.getTestDataVersionId(),
                        result.getStatus(), result.getTime(), result.getMemory(), result.getScore());
            } else {
                log.debug("更新判题进度成功，提交编号：{}，执行编号：{}，状态：{}，消息序号：{}",
                        dispatch.getSubmissionId(), dispatch.getExecutionId(), result.getStatus(), result.getSequence());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    private boolean saveDispatch(JudgeDispatch dispatch, JudgeResult result,
                                 JudgeStatus status, int state, String payload) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextAttempt = result.isTerminal()
                ? dispatch.getNextAttemptTime()
                : now.plus(properties.getResultWait());
        LocalDateTime published = dispatch.getPublishedTime() == null ? now : dispatch.getPublishedTime();
        return dispatches.lambdaUpdate()
                .eq(JudgeDispatch::getId, dispatch.getId())
                .eq(JudgeDispatch::getExecutionId, result.getExecutionId())
                .ne(JudgeDispatch::getDispatchState, JudgeDispatchState.COMPLETED)
                .lt(JudgeDispatch::getLastResultSequence, result.getSequence())
                .set(JudgeDispatch::getDispatchState, state)
                .set(JudgeDispatch::getStatus, status)
                .set(JudgeDispatch::getLastResultSequence, result.getSequence())
                .set(JudgeDispatch::getResultPayload, payload)
                .set(JudgeDispatch::getNextAttemptTime, nextAttempt)
                .set(JudgeDispatch::getPublishedTime, published)
                .set(JudgeDispatch::getLeaseToken, null)
                .set(JudgeDispatch::getLeaseUntil, null)
                .update();
    }

    private void saveSubmission(Judge judge, JudgeResult result, JudgeStatus status) {
        boolean saved = judges.lambdaUpdate()
                .eq(Judge::getId, judge.getId())
                .eq(Judge::getExecutionId, result.getExecutionId())
                .set(Judge::getStatus, status)
                .set(Judge::getTime, result.getTime())
                .set(Judge::getMemory, result.getMemory())
                .set(Judge::getScore, result.getScore())
                .set(Judge::getErrorMessage, result.getErrorMessage())
                .set(Judge::getJudgeServer, truncate(result.getWorkerId(), 100))
                .update();
        if (!saved) {
            throw new JudgeException("评测提交结果保存失败");
        }
        if (result.isTerminal()) {
            saveCases(judge.getId(), result.getTestCaseResults());
            if (status == JudgeStatus.STATUS_ACCEPTED && Objects.equals(judge.getContestId(), 0L)) {
                saveAccepted(judge);
            }
        }
    }

    /**
     * 每个新提交绑定一次执行，终态与测试点原子提交；重复结果由任务行锁和终态检查拒绝。
     * 不先删除空的索引范围，避免不同提交在 REPEATABLE READ 下取得重叠间隙锁后互相阻塞插入。
     */
    private void saveCases(Long submissionId, List<TestCaseResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        List<JudgeCase> records = new ArrayList<>(results.size());
        for (TestCaseResult result : results) {
            JudgeCase record = new JudgeCase();
            record.setJudgeId(submissionId);
            record.setCaseIndex(result.getCaseIndex());
            record.setStatus(status(result.getStatus()));
            record.setTime(result.getTime());
            record.setMemory(result.getMemory());
            record.setScore(result.getScore());
            record.setOutput(result.getOutput());
            record.setErrorMessage(result.getErrorMessage());
            records.add(record);
        }
        if (!cases.saveBatch(records)) {
            throw new JudgeException("评测测试点结果保存失败");
        }
    }

    /** 同一用户不同提交同时 AC 时由用户行锁串行判断，不重复产生业务记录。 */
    private void saveAccepted(Judge judge) {
        User user = users.lambdaQuery()
                .eq(User::getId, judge.getUserId())
                .last("FOR UPDATE")
                .one();
        if (user == null) {
            throw new JudgeException("评测用户已不存在");
        }
        boolean exists = accepted.lambdaQuery()
                .eq(UserAcproblem::getUserId, judge.getUserId())
                .eq(UserAcproblem::getProblemId, judge.getProblemId())
                .exists();
        if (!exists) {
            UserAcproblem record = UserAcproblem.builder()
                    .submissionId(judge.getId())
                    .userId(judge.getUserId())
                    .username(judge.getUsername())
                    .problemId(judge.getProblemId())
                    .displayId(judge.getDisplayId())
                    .build();
            if (!accepted.save(record)) {
                throw new JudgeException("用户通过记录保存失败");
            }
        }
    }

    public JudgeResultVO toView(JudgeDispatch dispatch) {
        JudgeResult result = null;
        if (dispatch.getResultPayload() != null) {
            try {
                result = codec.result(dispatch.getResultPayload());
                validate(result);
                if (!sameExecution(dispatch, result)) {
                    throw new IllegalArgumentException("持久结果与当前执行标识不一致");
                }
            } catch (IllegalArgumentException e) {
                result = null;
                log.warn("持久评测结果信息无法解析，提交编号：{}", dispatch.getSubmissionId());
            }
        }
        return toView(dispatch, result);
    }

    private JudgeResultVO toView(JudgeDispatch dispatch, JudgeResult result) {
        JudgeResultVO view = JudgeResultVO.builder()
                .status(dispatch.getStatus())
                .build();
        if (result != null) {
            view.setTime(result.getTime());
            view.setMemory(result.getMemory());
            view.setErrorMessage(result.getErrorMessage());
            if (Objects.equals(dispatch.getStrategy(), JudgeDispatchState.SELF_TEST)
                    && result.getTestCaseResults() != null && !result.getTestCaseResults().isEmpty()) {
                view.setOutput(result.getTestCaseResults().get(0).getOutput());
            }
        }
        if (Objects.equals(dispatch.getStrategy(), JudgeDispatchState.SELF_TEST)) {
            try {
                JudgeTask task = codec.task(dispatch.getTaskPayload());
                if (task.getSchemaVersion() != JudgeProtocol.SCHEMA_VERSION
                        || !Objects.equals(task.getSubmissionId(), dispatch.getSubmissionId())
                        || !Objects.equals(task.getExecutionId(), dispatch.getExecutionId())
                        || !Objects.equals(task.getStrategy(), dispatch.getStrategy())) {
                    throw new IllegalArgumentException("持久自测输入与当前执行标识不一致");
                }
                view.setInput(task.getInput());
                view.setExpectedOutput(task.getExpectedOutput());
            } catch (IllegalArgumentException e) {
                log.warn("持久自测输入信息无法解析，提交编号：{}", dispatch.getSubmissionId());
            }
        }
        return view;
    }

    private JudgeResult systemError(JudgeDispatch dispatch, String reason) {
        JudgeResult result = new JudgeResult();
        result.setSchemaVersion(JudgeProtocol.SCHEMA_VERSION);
        result.setSubmissionId(dispatch.getSubmissionId());
        result.setExecutionId(dispatch.getExecutionId());
        result.setProblemId(dispatch.getProblemId());
        result.setTestDataVersionId(dispatch.getTestDataVersionId());
        result.setStrategy(dispatch.getStrategy());
        result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
        result.setTerminal(true);
        result.setSequence(3);
        result.setEmittedAtEpochMs(System.currentTimeMillis());
        result.setWorkerId("web-recovery");
        result.setErrorMessage(reason);
        return result;
    }

    private void validate(JudgeResult result) {
        validateIdentity(result);
        validateState(result);
        nonNegative(result.getTime());
        nonNegative(result.getMemory());
        validateScore(result.getScore());
        validateCases(result.getTestCaseResults());
    }

    /**
     * 校验结果协议、执行标识及受支持的策略，不能用数值区间代替策略定义。
     * @param result 待处理结果，不允许 null
     * @throws IllegalArgumentException 协议或标识不合法
     */
    private void validateIdentity(JudgeResult result) {
        if (result == null || result.getSchemaVersion() != JudgeProtocol.SCHEMA_VERSION
                || result.getSubmissionId() == null || result.getSubmissionId() <= 0
                || result.getExecutionId() == null
                || !result.getExecutionId().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || (!Objects.equals(result.getStrategy(), JudgeDispatchState.FORMAL)
                && !Objects.equals(result.getStrategy(), JudgeDispatchState.SELF_TEST))
                || result.getStatus() == null || result.getSequence() < 1 || result.getSequence() > 3) {
            throw new IllegalArgumentException("评测结果标识或协议版本无效");
        }
    }

    private void validateState(JudgeResult result) {
        boolean progress = !result.isTerminal()
                && ((result.getSequence() == 1 && result.getStatus() == JudgeVerdict.STATUS_COMPILING)
                || (result.getSequence() == 2 && result.getStatus() == JudgeVerdict.STATUS_JUDGING));
        if (!progress && !(result.isTerminal() && result.getSequence() == 3
                && TERMINAL.contains(result.getStatus()))) {
            throw new IllegalArgumentException("评测结果状态与消息序号不匹配");
        }
    }

    private void validateCases(List<TestCaseResult> testCases) {
        if (testCases == null) {
            return;
        }
        if (testCases.size() > MAX_CASES) {
            throw new IllegalArgumentException("评测测试点数量超出限制");
        }
        Set<Integer> indices = new HashSet<>();
        for (TestCaseResult testCase : testCases) {
            if (testCase == null || testCase.getCaseIndex() == null || testCase.getCaseIndex() <= 0
                    || !indices.add(testCase.getCaseIndex()) || testCase.getStatus() == null
                    || !TERMINAL.contains(testCase.getStatus())) {
                throw new IllegalArgumentException("评测测试点标识或状态无效");
            }
            nonNegative(testCase.getTime());
            nonNegative(testCase.getMemory());
            validateScore(testCase.getScore());
        }
    }

    private void validateScore(Integer score) {
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("评测结果得分无效");
        }
    }

    private void limitPreviews(JudgeResult result) {
        result.setErrorMessage(truncate(result.getErrorMessage(), 16_384));
        if (result.getTestCaseResults() != null) {
            for (TestCaseResult testCase : result.getTestCaseResults()) {
                testCase.setOutput(truncate(testCase.getOutput(),
                        Objects.equals(result.getStrategy(), JudgeDispatchState.SELF_TEST) ? 65_536 : 4_096));
                testCase.setErrorMessage(truncate(testCase.getErrorMessage(), 4_096));
            }
        }
    }

    private void nonNegative(Long value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException("评测资源统计不得为负数");
        }
    }

    private JudgeStatus status(JudgeVerdict verdict) {
        for (JudgeStatus status : JudgeStatus.values()) {
            if (status.getCode() == verdict.getCode()) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的评测状态");
    }

    private String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
}
