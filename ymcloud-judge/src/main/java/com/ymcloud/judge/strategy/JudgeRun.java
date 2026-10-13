package com.ymcloud.judge.strategy;

import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.gojudge.GoJudgeCapacityException;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import lombok.extern.slf4j.Slf4j;
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
 * Worker 执行边界：使用固定数据快照并产生结果，不访问业务数据库。
 */
@Slf4j
@Component
public class JudgeRun {

    private final GoJudgeExecutor executor;
    private final TestDataCache cache;
    private final WorkerProperties properties;

    public JudgeRun(GoJudgeExecutor executor, TestDataCache cache, WorkerProperties properties) {
        this.executor = executor;
        this.cache = cache;
        this.properties = properties;
    }

    public JudgeResult execute(JudgeTask task, Consumer<JudgeResult> progress) throws IOException {
        long started = System.nanoTime();
        log.info("开始执行判题 execution={} submission={} problem={} version={} strategy={} language={}",
                task.getExecutionId(),
                task.getSubmissionId(),
                task.getProblemId(),
                task.getTestDataVersionId(),
                task.getStrategy(),
                task.getLanguageConfig().getName());
        TestDataCache.Lease lease = null;
        GoJudgeTaskSession session = null;
        String stage = "prepare";
        Integer caseIndex = null;
        JudgeResult result = result(task, JudgeVerdict.STATUS_PENDING, false);
        try {
            checkDeadline(task);
            String capacityFailure = executor.capacityFailure(task);
            if (capacityFailure != null) {
                return systemFailure(task, capacityFailure);
            }
            List<CaseSource> cases = new ArrayList<>();
            if (task.getStrategy() == 1) {
                cases.add(new CaseSource(
                        1,
                        () -> inline(task.getInput()),
                        () -> inline(task.getExpectedOutput())));
            } else {
                lease = cache.acquire(task.getProblemId(), task.getTestDataVersionId(), task.getManifestSha256());
                for (TestDataCache.CachedCase testCase : lease.entries()) {
                    cases.add(new CaseSource(testCase.id(), testCase::openInput, testCase::openOutput));
                }
            }
            if (cases.isEmpty()) {
                throw new IOException("固定版本没有测试数据");
            }
            log.info("判题数据准备完成 execution={} cases={} elapsedMs={}",
                    task.getExecutionId(),
                    cases.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            checkDeadline(task);
            progress.accept(result(task, JudgeVerdict.STATUS_COMPILING, false));
            session = executor.openTask(task);
            stage = "compile";
            long compileStarted = System.nanoTime();
            log.debug("开始编译代码 execution={}", task.getExecutionId());
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            log.info("代码编译完成 execution={} elapsedMs={}", task.getExecutionId(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - compileStarted));
            progress.accept(result(task, JudgeVerdict.STATUS_JUDGING, false));
            List<TestCaseResult> results = new ArrayList<>();
            long maxTime = 0;
            long maxMemory = 0;
            int outputBudget = 512 * 1024;
            for (CaseSource source : cases) {
                stage = "case";
                caseIndex = source.id();
                checkDeadline(task);
                TestCaseResult current = executeCase(task, source, session, program);
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
            result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
            result.setErrorMessage(e.getMessage());
            result.setScore(0);
        } catch (CompileException e) {
            result.setStatus(JudgeVerdict.STATUS_COMPILE_ERROR);
            result.setErrorMessage(e.getMessage());
            result.setScore(0);
        } catch (IOException | RuntimeException e) {
            log.warn("判题执行异常 execution={} stage={} case={} elapsedMs={} cause={}", task.getExecutionId(),
                    stage, caseIndex, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    e.getClass().getSimpleName());
            throw e;
        } finally {
            boolean interrupted = Thread.interrupted();
            if (session != null) {
                session.close();
            }
            if (lease != null) {
                try {
                    lease.close();
                } catch (IOException e) {
                    log.warn("释放本次数据缓存失败 execution={} cause={}",
                            task.getExecutionId(), e.getClass().getSimpleName());
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
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

    public JudgeResult systemFailure(JudgeTask task, String message) {
        JudgeResult result = result(task, JudgeVerdict.STATUS_SYSTEM_ERROR, true);
        result.setScore(0);
        result.setErrorMessage(message);
        return result;
    }

    public JudgeResult result(JudgeTask task, JudgeVerdict status, boolean terminal) {
        JudgeResult result = new JudgeResult();
        result.setExecutionId(task.getExecutionId());
        result.setSubmissionId(task.getSubmissionId());
        result.setProblemId(task.getProblemId());
        result.setTestDataVersionId(task.getTestDataVersionId());
        result.setStrategy(task.getStrategy());
        result.setWorkerId(properties.getId());
        result.setTerminal(terminal);
        if (terminal) {
            result.setSequence(3);
        } else {
            result.setSequence(status == JudgeVerdict.STATUS_COMPILING ? 1 : 2);
        }
        result.setStatus(status);
        result.setEmittedAtEpochMs(System.currentTimeMillis());
        result.setTime(0L);
        result.setMemory(0L);
        return result;
    }

    static void score(JudgeResult result, int mode) {
        List<TestCaseResult> cases = result.getTestCaseResults();
        long accepted = cases.stream().filter(testCase -> testCase.getStatus() == JudgeVerdict.STATUS_ACCEPTED).count();
        if (mode == 1) {
            result.setScore((int) Math.ceil((double) accepted / cases.size() * 100));
            if (accepted == cases.size()) {
                result.setStatus(JudgeVerdict.STATUS_ACCEPTED);
            } else if (accepted == 0) {
                result.setStatus(JudgeVerdict.STATUS_WRONG_ANSWER);
            } else {
                result.setStatus(JudgeVerdict.STATUS_PARTIAL_ACCEPTED);
            }
        } else {
            result.setStatus(JudgeVerdict.STATUS_ACCEPTED);
            result.setScore(100);
            for (TestCaseResult testCase : cases) {
                if (testCase.getStatus() != JudgeVerdict.STATUS_ACCEPTED) {
                    result.setStatus(testCase.getStatus());
                    result.setScore(0);
                    break;
                }
            }
        }
    }

    private TestCaseResult executeCase(JudgeTask task, CaseSource source, GoJudgeTaskSession session,
                                       GoJudgeExecutor.PreparedProgram program) throws IOException {
        long started = System.nanoTime();
        TestCaseResult result = new TestCaseResult();
        result.setJudgeId(task.getSubmissionId());
        result.setCaseIndex(source.id());
        try (InputStream input = source.input().open()) {
            GoJudgeExecutor.ExecutionResult run = executor.run(session, program, input);
            result.setTime(run.timeMillis());
            result.setMemory(run.memoryKiB());
            result.setStatus(run.status());
            result.setErrorMessage(run.errorMessage());
            if (run.status() == JudgeVerdict.STATUS_ACCEPTED) {
                try (InputStream expected = source.output().open()) {
                    result.setStatus(OutputComparator.matches(expected, run.stdout())
                            ? JudgeVerdict.STATUS_ACCEPTED : JudgeVerdict.STATUS_WRONG_ANSWER);
                }
            }
            result.setOutput(preview(run.stdout()));
        }
        result.setScore(result.getStatus() == JudgeVerdict.STATUS_ACCEPTED ? 100 : 0);
        log.debug("测试点执行完成 execution={} version={} case={} status={} timeMs={} memoryKiB={} elapsedMs={}",
                task.getExecutionId(), task.getTestDataVersionId(), source.id(), result.getStatus(), result.getTime(),
                result.getMemory(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return result;
    }

    private static InputStream inline(String value) {
        return new ByteArrayInputStream((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String preview(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "\n... (output truncated)";
    }

    private static void checkDeadline(JudgeTask task) throws IOException {
        if (task.getDeadlineEpochMs() <= System.currentTimeMillis() || Thread.currentThread().isInterrupted()) {
            throw new IOException("任务超过总截止时间或失去执行租约");
        }
    }

    private record CaseSource(int id, StreamSource input, StreamSource output) {
    }

    @FunctionalInterface
    private interface StreamSource {
        InputStream open() throws IOException;
    }
}
