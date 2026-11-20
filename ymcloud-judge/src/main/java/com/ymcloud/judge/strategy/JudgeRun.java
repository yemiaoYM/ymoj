package com.ymcloud.judge.strategy;

import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.gojudge.GoJudgeCapacityException;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.pojo.bo.judge.JudgeCaseResult;
import com.ymcloud.pojo.bo.judge.JudgeResult;
import com.ymcloud.pojo.bo.judge.JudgeTask;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-06
 * @Description: Worker 执行边界：使用固定数据快照并产生结果，不访问业务数据库。
 */
@Slf4j
@Component
public class JudgeRun {

    @Autowired
    private GoJudgeExecutor executor;

    @Autowired
    private TestDataCache cache;

    @Autowired
    private WorkerProperties properties;

    /**
     * 使用本次开跑时的清单完成判题，发布进度、保存终态并释放资源。
     * @param task 已通过入口校验的任务
     * @param progress 进度发布回调，失败应向上传播
     * @return 本次执行的终态结果
     * @throws IOException 传输、数据或资源清理失败，交任务边界有限恢复
     */
    public JudgeResult execute(JudgeTask task, Consumer<JudgeResult> progress) throws IOException {
        long started = System.nanoTime();
        log.info("开始执行判题 execution={} submission={} problem={} strategy={} language={}",
                task.getExecutionId(),
                task.getSubmissionId(),
                task.getProblemId(),
                task.getStrategy(),
                task.getLanguage());
        TestDataCache.Lease lease = null;
        GoJudgeTaskSession session = null;
        String stage = "prepare";
        Integer caseIndex = null;
        JudgeResult result = result(task, JudgeStatus.STATUS_PENDING, false);
        try {
            checkDeadline(task);
            String capacityFailure = executor.capacityFailure(task);
            if (capacityFailure != null) {
                return systemFailure(task, capacityFailure);
            }
            session = executor.openTask(task);
            List<CaseSource> cases = new ArrayList<>();
            if (task.getStrategy() == 1) {
                cases.add(new CaseSource(
                        1,
                        () -> inline(task.getInput()),
                        () -> inline(task.getExpectedOutput())));
            } else {
                lease = cache.acquire(task.getProblemId());
                for (TestDataCache.CachedCase testCase : lease.entries()) {
                    cases.add(new CaseSource(testCase.id(), testCase::openInput, testCase::openOutput));
                }
            }
            if (cases.isEmpty()) {
                throw new IOException("当前题目没有可执行的测试点");
            }
            log.info("判题数据准备完成 execution={} cases={} elapsedMs={}",
                    task.getExecutionId(),
                    cases.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            checkDeadline(task);
            progress.accept(result(task, JudgeStatus.STATUS_COMPILING, false));
            stage = "compile";
            long compileStarted = System.nanoTime();
            log.debug("开始编译代码 execution={}", task.getExecutionId());
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            GoJudgeExecutor.PreparedProgram checker = null;
            if (task.getSpj() != null) {
                stage = "spj-compile";
                try {
                    checker = executor.compileSpj(session, task.getSpj());
                } catch (CompileException e) {
                    return systemFailure(task, "SPJ 编译失败，请联系题目管理员");
                }
            }
            log.info("代码编译完成 execution={} elapsedMs={}", task.getExecutionId(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - compileStarted));
            progress.accept(result(task, JudgeStatus.STATUS_JUDGING, false));
            List<JudgeCaseResult> results = new ArrayList<>();
            long maxTime = 0;
            long maxMemory = 0;
            int outputBudget = 512 * 1024;
            for (CaseSource source : cases) {
                stage = "case";
                caseIndex = source.getId();
                checkDeadline(task);
                JudgeCaseResult current = executeCase(task, source, session, program, checker);
                if (current.getStatus() == JudgeStatus.STATUS_SYSTEM_ERROR) {
                    return systemFailure(task, "SPJ 校验程序运行失败，请联系题目管理员");
                }
                String output = current.getOutput();
                if (output != null) {
                    if (output.length() <= outputBudget) {
                        outputBudget -= output.length();
                    } else {
                        current.setOutput(null);
                    }
                }
                results.add(current);
                maxTime = Math.max(maxTime, current.getTime());
                maxMemory = Math.max(maxMemory, current.getMemory());
            }
            result.setTestCaseResults(results);
            result.setTime(maxTime);
            result.setMemory(maxMemory);
            stage = "score";
            caseIndex = null;
            score(result, task.getMode());
        } catch (GoJudgeCapacityException e) {
            result.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
            result.setErrorMessage(e.getMessage());
            result.setScore(0);
        } catch (CompileException e) {
            result.setStatus(JudgeStatus.STATUS_COMPILE_ERROR);
            result.setErrorMessage(e.getMessage());
            result.setScore(0);
        } catch (IOException | RuntimeException e) {
            log.warn("判题执行异常 execution={} stage={} case={} elapsedMs={} cause={}", task.getExecutionId(),
                    stage, caseIndex, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    e.getClass().getSimpleName());
            throw e;
        } finally {
            boolean interrupted = Thread.interrupted();
            try {
                if (lease != null) {
                    try {
                        lease.close();
                    } catch (IOException e) {
                        log.warn("释放本次数据缓存失败 execution={} cause={}",
                                task.getExecutionId(), e.getClass().getSimpleName());
                    }
                }
            } finally {
                try {
                    if (session != null) {
                        session.close();
                    }
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        result.setTerminal(true);
        result.setSequence(3);
        result.setEmittedAtEpochMs(System.currentTimeMillis());
        log.info("判题执行完成 execution={} status={} score={} timeMs={} memoryKiB={} elapsedMs={}",
                task.getExecutionId(), result.getStatus(), result.getScore(), result.getTime(), result.getMemory(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return result;
    }

    /**
     * 按当前任务身份生成安全系统错误终态，不复用其他执行的结果。
     * @param task 当前任务快照
     * @param message 可向用户展示的安全提示
     * @return 得分为零的系统错误终态
     */
    public JudgeResult systemFailure(JudgeTask task, String message) {
        JudgeResult result = result(task, JudgeStatus.STATUS_SYSTEM_ERROR, true);
        result.setScore(0);
        result.setErrorMessage(message);
        return result;
    }

    /**
     * 生成带固定身份的结果骨架，进度与终态使用约定序号。
     * @param task 当前任务快照
     * @param status 业务判题状态
     * @param terminal 是否为终态
     * @return 包含执行身份和 Worker 标识的结果
     */
    public JudgeResult result(JudgeTask task, JudgeStatus status, boolean terminal) {
        JudgeResult result = new JudgeResult();
        result.setExecutionId(task.getExecutionId());
        result.setSubmissionId(task.getSubmissionId());
        result.setProblemId(task.getProblemId());
        result.setStrategy(task.getStrategy());
        result.setWorkerId(properties.getId());
        result.setTerminal(terminal);
        if (terminal) {
            result.setSequence(3);
        } else {
            result.setSequence(status == JudgeStatus.STATUS_COMPILING ? 1 : 2);
        }
        result.setStatus(status);
        result.setEmittedAtEpochMs(System.currentTimeMillis());
        result.setTime(0L);
        result.setMemory(0L);
        return result;
    }

    /**
     * 按既有 ACM 或测试点计分规则汇总结果，不改变 OI/IOI 暂共用语义。
     * @param result 包含非空测试点列表的结果，原地更新状态和分数
     * @param mode 评分协议，0=ACM，1=测试点计分
     */
    static void score(JudgeResult result, int mode) {
        List<JudgeCaseResult> cases = result.getTestCaseResults();
        long accepted = cases.stream().filter(testCase -> testCase.getStatus() == JudgeStatus.STATUS_ACCEPTED).count();
        if (mode == 1) {
            result.setScore((int) Math.ceil((double) accepted / cases.size() * 100));
            if (accepted == cases.size()) {
                result.setStatus(JudgeStatus.STATUS_ACCEPTED);
            } else if (accepted == 0) {
                result.setStatus(JudgeStatus.STATUS_WRONG_ANSWER);
            } else {
                result.setStatus(JudgeStatus.STATUS_PARTIAL_ACCEPTED);
            }
        } else {
            result.setStatus(JudgeStatus.STATUS_ACCEPTED);
            result.setScore(100);
            for (JudgeCaseResult testCase : cases) {
                if (testCase.getStatus() != JudgeStatus.STATUS_ACCEPTED) {
                    result.setStatus(testCase.getStatus());
                    result.setScore(0);
                    break;
                }
            }
        }
    }

    /**
     * 执行单个测试点并流式比对答案，结果仅保留受限输出预览。
     * @param task 当前任务快照
     * @param source 当前测试点输入和答案来源
     * @param session 当前沙箱会话
     * @param program 已编译程序
     * @param checker 校验器产物，普通判题为 null
     * @return 测试点判定、分数及资源统计
     * @throws IOException 输入、执行或答案完整性校验失败
     */
    private JudgeCaseResult executeCase(JudgeTask task, CaseSource source, GoJudgeTaskSession session,
                                       GoJudgeExecutor.PreparedProgram program,
                                       GoJudgeExecutor.PreparedProgram checker) throws IOException {
        long started = System.nanoTime();
        JudgeCaseResult result = new JudgeCaseResult();
        result.setJudgeId(task.getSubmissionId());
        result.setCaseIndex(source.getId());
        try (InputStream input = source.getInput().open()) {
            GoJudgeExecutor.ExecutionResult run = executor.run(session, program, input);
            result.setTime(run.getTimeMillis());
            result.setMemory(run.getMemoryKiB());
            result.setStatus(run.getStatus());
            result.setErrorMessage(run.getErrorMessage());
            try {
                if (run.getStatus() == JudgeStatus.STATUS_ACCEPTED) {
                    try (InputStream expected = source.getOutput().open()) {
                        if (checker == null) {
                            try (InputStream actual = session.openFile(run.getStdoutFileId())) {
                                result.setStatus(OutputComparator.matches(expected, actual)
                                        ? JudgeStatus.STATUS_ACCEPTED : JudgeStatus.STATUS_WRONG_ANSWER);
                            }
                        } else {
                            try (InputStream testInput = source.getInput().open()) {
                                result.setStatus(executor.check(session, checker, testInput, expected, run.getStdoutFileId()));
                            }
                        }
                    }
                }
            } finally {
                if (run.getStdoutFileId() != null) {
                    session.release(run.getStdoutFileId());
                }
            }
            result.setOutput(preview(run.getStdout()));
        }
        result.setScore(result.getStatus() == JudgeStatus.STATUS_ACCEPTED ? 100 : 0);
        log.debug("测试点执行完成 execution={} case={} status={} timeMs={} memoryKiB={} elapsedMs={}",
                task.getExecutionId(),
                source.getId(),
                result.getStatus(),
                result.getTime(),
                result.getMemory(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return result;
    }

    /**
     * 将有界自测文本转换为 UTF-8 流。
     * @param value 自测文本，null 按空串处理
     * @return 由调用方关闭的内存流
     */
    private static InputStream inline(String value) {
        return new ByteArrayInputStream((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 限制测试点输出预览长度，完整输出不进入结果消息。
     * @param value 受沙箱输出限额约束的非 null 文本
     * @return 最多 300 个原文字符及截断标记
     */
    private static String preview(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "\n... (output truncated)";
    }

    /**
     * 在阶段边界检查任务截止和租约中断。
     * @param task 当前任务快照
     * @throws IOException 任务到期或线程被中断
     */
    private static void checkDeadline(JudgeTask task) throws IOException {
        if (task.getDeadlineEpochMs() <= System.currentTimeMillis() || Thread.currentThread().isInterrupted()) {
            throw new IOException("任务超过总截止时间或失去执行租约");
        }
    }

    /**
     * 供当前执行使用的测试点及其输入输出流来源
     */
    @Value
    private static class CaseSource {

        /**
         * 从 1 开始的测试点序号
         */
        int id;

        /**
         * 测试点输入流来源
         */
        StreamSource input;

        /**
         * 预期输出流来源
         */
        StreamSource output;
    }

    @FunctionalInterface
    private interface StreamSource {
        /**
         * 按需打开当前测试点数据，调用方负责关闭。
         * @return 输入或答案流
         * @throws IOException 数据无法打开或验证失败
         */
        InputStream open() throws IOException;
    }
}
