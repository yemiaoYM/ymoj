package com.ymcloud.judge.strategy;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.judge.bo.CodeRunResult;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.exception.MemoryLimitExceededException;
import com.ymcloud.judge.exception.RuntimeErrorException;
import com.ymcloud.judge.exception.SandBoxException;
import com.ymcloud.judge.exception.TimeLimitExceededException;
import com.ymcloud.judge.io.WorkerPaths;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
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

    private final DockerClient docker;
    private final TestDataCache cache;
    private final WorkerProperties properties;

    public JudgeRun(DockerClient docker, TestDataCache cache, WorkerProperties properties) {
        this.docker = docker;
        this.cache = cache;
        this.properties = properties;
    }

    public JudgeResult execute(JudgeTask task, Consumer<JudgeResult> progress) throws IOException {
        long started = System.nanoTime();
        log.info("开始执行判题 execution={} submission={} problem={} version={} strategy={} language={}",
                task.getExecutionId(), task.getSubmissionId(), task.getProblemId(), task.getTestDataVersionId(),
                task.getStrategy(), task.getLanguageConfig().getName());
        TestDataCache.Lease lease = null;
        Path working = null;
        CodeExecContainer container = null;
        String stage = "prepare";
        Integer caseIndex = null;
        JudgeResult result = result(task, JudgeVerdict.STATUS_PENDING, false);
        try {
            checkDeadline(task);
            List<CaseSource> cases = new ArrayList<>();
            if (task.getStrategy() == 1) {
                cases.add(new CaseSource(1, () -> inline(task.getInput()), () -> inline(task.getExpectedOutput())));
            } else {
                lease = cache.acquire(task.getProblemId(), task.getTestDataVersionId(), task.getManifestSha256());
                for (TestDataCache.CachedCase testCase : lease.entries()) {
                    cases.add(new CaseSource(testCase.id(), testCase::openInput, testCase::openOutput));
                }
            }
            if (cases.isEmpty()) {
                throw new IOException("固定版本没有测试数据");
            }
            log.info("判题数据准备完成 execution={} cases={} elapsedMs={}", task.getExecutionId(), cases.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            checkDeadline(task);
            stage = "container";
            working = createWorkingDirectory(task);
            progress.accept(result(task, JudgeVerdict.STATUS_COMPILING, false));
            container = container(task, working);
            SandBox sandbox = new SandBoxFactory(task.getLanguageConfig().getName()).createSandBox(container);
            sandbox.saveCode(task.getCode());
            stage = "compile";
            long compileStarted = System.nanoTime();
            log.debug("开始编译代码 execution={}", task.getExecutionId());
            sandbox.compile(container, remaining(task));
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
                // 被 OOM、输出限制或强制截止杀掉的容器不能供下一测试点使用。
                if (!container.isRunning()) {
                    log.debug("重建已停止的判题容器 execution={} case={}", task.getExecutionId(), source.id());
                    container.remove(true);
                    container = container(task, working);
                    sandbox = new SandBoxFactory(task.getLanguageConfig().getName()).createSandBox(container);
                }
                TestCaseResult current = executeCase(task, source, sandbox, container);
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
            if (container != null) {
                try {
                    container.remove(true);
                } catch (RuntimeException e) {
                    log.warn("清理本次沙箱失败 execution={} cause={}",
                            task.getExecutionId(), e.getClass().getSimpleName());
                }
            }
            if (lease != null) {
                try {
                    lease.close();
                } catch (IOException e) {
                    log.warn("释放本次数据缓存失败 execution={} cause={}",
                            task.getExecutionId(), e.getClass().getSimpleName());
                }
            }
            if (working != null) {
                try {
                    deleteWorkingDirectory(working);
                } catch (IOException e) {
                    log.warn("清理本次工作目录失败 execution={} cause={}",
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

    private CodeExecContainer container(JudgeTask task, Path working) {
        CodeExecContainer container = new CodeExecContainerFactory(docker, working.toString(),
                properties.getContainerWorkingDir(), task.getMemoryLimit(), task.getLanguageConfig().getEnv())
                .createDockerContainer();
        container.setMaxOutputBytes(properties.getMaxOutputBytes());
        log.debug("创建判题容器 execution={} container={}", task.getExecutionId(), container.getContainerId());
        try {
            container.start();
            return container;
        } catch (RuntimeException startFailure) {
            // 创建成功但启动失败的容器尚未交给 execute，必须在此处释放。
            try {
                container.remove(true);
            } catch (RuntimeException cleanupFailure) {
                startFailure.addSuppressed(cleanupFailure);
            }
            throw startFailure;
        }
    }

    private Path createWorkingDirectory(JudgeTask task) throws IOException {
        Path root = properties.getWorkDir().toAbsolutePath().normalize();
        WorkerPaths.rejectSymbolicLinks(root);
        Files.createDirectories(root);
        WorkerPaths.rejectSymbolicLinks(root);
        Path working = Files.createTempDirectory(root.toRealPath(), "execution-" + task.getExecutionId() + "-");
        try {
            if (working.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(working, PosixFilePermissions.fromString("rwx------"));
            }
            return working;
        } catch (IOException permissionFailure) {
            try {
                Files.deleteIfExists(working);
            } catch (IOException cleanupFailure) {
                permissionFailure.addSuppressed(cleanupFailure);
            }
            throw permissionFailure;
        }
    }

    private TestCaseResult executeCase(JudgeTask task, CaseSource source, SandBox sandbox,
                                       CodeExecContainer container) throws IOException {
        long started = System.nanoTime();
        TestCaseResult result = new TestCaseResult();
        result.setJudgeId(task.getSubmissionId());
        result.setCaseIndex(source.id());
        result.setTime(0L);
        result.setMemory(0L);
        try (InputStream input = source.input().open()) {
            CodeRunResult run = sandbox.execute(container, input, task.getTimeLimit() + 200L, remaining(task));
            result.setTime(run.getTimeUsed());
            result.setMemory(run.getMemoryUsed());
            if (run.getTimeUsed() > task.getTimeLimit()) {
                result.setStatus(JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED);
            } else if (run.getMemoryUsed() > task.getMemoryLimit()) {
                result.setStatus(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED);
            } else {
                try (InputStream expected = source.output().open()) {
                    result.setStatus(OutputComparator.matches(expected, run.getStdout())
                            ? JudgeVerdict.STATUS_ACCEPTED : JudgeVerdict.STATUS_WRONG_ANSWER);
                }
            }
            result.setOutput(preview(run.getStdout()));
        } catch (TimeLimitExceededException e) {
            result.setStatus(JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED);
        } catch (MemoryLimitExceededException e) {
            result.setStatus(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED);
        } catch (RuntimeErrorException e) {
            result.setStatus(JudgeVerdict.STATUS_RUNTIME_ERROR);
            result.setErrorMessage(e.getMessage());
        }
        result.setScore(result.getStatus() == JudgeVerdict.STATUS_ACCEPTED ? 100 : 0);
        log.debug("测试点执行完成 execution={} case={} status={} timeMs={} memoryKiB={} elapsedMs={}",
                task.getExecutionId(), source.id(), result.getStatus(), result.getTime(), result.getMemory(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return result;
    }

    private static InputStream inline(String value) {
        return new ByteArrayInputStream((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String preview(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "\n... (output truncated)";
    }

    private static long remaining(JudgeTask task) {
        long remaining = task.getDeadlineEpochMs() - System.currentTimeMillis();
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
            throw new SandBoxException("任务超过总截止时间或失去执行租约");
        }
        return remaining;
    }

    private static void checkDeadline(JudgeTask task) {
        remaining(task);
    }

    void deleteWorkingDirectory(Path working) throws IOException {
        Path configuredRoot = properties.getWorkDir().toAbsolutePath().normalize();
        WorkerPaths.rejectSymbolicLinks(configuredRoot);
        Path root = configuredRoot.toRealPath();
        if (!root.equals(working.getParent()) || !working.getFileName().toString().startsWith("execution-")) {
            throw new IOException("拒绝清理 Worker 工作目录以外的路径");
        }
        // 默认不跟随符号链接，逐文件删除；大量执行产物不在清理时汇集到内存。
        Files.walkFileTree(working, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record CaseSource(int id, StreamSource input, StreamSource output) {
    }

    @FunctionalInterface
    private interface StreamSource {
        InputStream open() throws IOException;
    }
}
