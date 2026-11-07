package com.ymcloud.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.gojudge.GoJudgeClient;
import com.ymcloud.judge.gojudge.GoJudgeException;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeFileMissingException;
import com.ymcloud.judge.gojudge.GoJudgeProperties;
import com.ymcloud.judge.gojudge.GoJudgeStartupCheck;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.judge.strategy.OutputComparator;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 使用独立的真实 go-judge 验证四语言入口；不会管理 Docker 或使用业务服务。
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_RUN_GO_JUDGE_TESTS", matches = "true")
class GoJudgeSandboxIntegrationTest {

    private RecordingGoJudgeClient client;
    private GoJudgeExecutor executor;
    private GoJudgeProperties properties;

    /**
     * 从显式环境配置连接独立沙箱，使用当前 Worker 的默认输出容量。
     */
    @BeforeEach
    void setup() {
        properties = new GoJudgeProperties();
        properties.setBaseUrl(System.getenv().getOrDefault("YMCLOUD_GO_JUDGE_URL", "http://127.0.0.1:5050"));
        properties.setToken(System.getenv("YMCLOUD_GO_JUDGE_TOKEN"));
        client = new RecordingGoJudgeClient(properties);
        executor = createGoJudgeExecutor(client, properties, new WorkerProperties());
    }

    @Test
    void deployedServicePassesMandatoryStartupChecks() throws Exception {
        createGoJudgeStartupCheck(client, properties, new WorkerProperties()).verify();
    }

    @AfterEach
    void close() {
        if (client != null) {
            client.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void reusesCompleteArtifactWithEmptyUnicodeAndExactWhitespace(String language) throws Exception {
        JudgeTask task = task(language, echo(language));
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            for (String input : new String[]{"", "中文🙂\n", " \tfirst\r\nlast  \n\n"}) {
                GoJudgeExecutor.ExecutionResult result = run(session, program, input);
                assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status(), language);
                assertEquals(input, result.stdout());
                assertTrue(result.timeMillis() >= 0 && result.memoryKiB() > 0);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void syntaxErrorsAreCompileErrors(String language) throws Exception {
        JudgeTask task = task(language, "this is invalid source !!!");
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            assertThrows(CompileException.class, () -> executor.compile(session));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void busyLoopsAndSleepingUseWallClockLimit(String language) throws Exception {
        for (String source : new String[]{busyLoop(language), sleep(language)}) {
            JudgeTask task = task(language, source);
            task.setTimeLimit(500L);
            try (GoJudgeTaskSession session = executor.openTask(task)) {
                GoJudgeExecutor.PreparedProgram program = executor.compile(session);
                assertEquals(JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED, run(session, program, "").status(), language);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void allocationBeyondCgroupBudgetIsMemoryLimit(String language) throws Exception {
        JudgeTask task = task(language, memory(language));
        task.setMemoryLimit(128 * 1024L);
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            assertEquals(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED, run(session, program, "").status(), language);
        }
    }

    /**
     * 验证进程崩溃及两个输出通道合计超过 32 MiB 时被终止。
     * @param language 受支持的选手语言
     * @throws Exception 沙箱请求或编译失败
     */
    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void crashesAndCombinedOutputOverflowAreRuntimeErrors(String language) throws Exception {
        for (String source : new String[]{crash(language), outputOverflow(language)}) {
            try (GoJudgeTaskSession session = executor.openTask(task(language, source))) {
                GoJudgeExecutor.PreparedProgram program = executor.compile(session);
                assertEquals(JudgeVerdict.STATUS_RUNTIME_ERROR, run(session, program, "").status(), language);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void eachPointGetsFreshWritableFiles(String language) throws Exception {
        try (GoJudgeTaskSession session = executor.openTask(task(language, pollution(language)))) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            for (int point = 0; point < 3; point++) {
                GoJudgeExecutor.ExecutionResult result = run(session, program, "");
                assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
                assertEquals("fresh", result.stdout());
            }
        }
    }

    @Test
    void missingArtifactRequiresOriginalPreparationAndDoesNotBecomeWrongAnswer() throws Exception {
        JudgeTask task = task("Python", echo("Python"));
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            client.delete(program.fileId(), task.getDeadlineEpochMs());
            assertThrows(IOException.class, () -> run(session, program, "retry\n"));
        }
        try (GoJudgeTaskSession retry = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(retry);
            assertEquals("retry\n", run(retry, program, "retry\n").stdout());
        }
    }

    /**
     * 验证两百万个九位整数可完整进入普通双流比较与 testlib 校验器，正文不进入结果 JSON。
     * @throws Exception 沙箱请求、编译或输出读取失败
     */
    @Test
    void twentyMegabyteStdoutIsCompleteAndPrintedMarkersCannotChangeVerdict() throws Exception {
        String source = "import sys\nsys.stdout.buffer.write(b'100000000 ' * 2000000)\n"
                + "sys.stderr.write('java.lang.OutOfMemoryError __YMCLOUD_TIME__=999999')\n";
        try (GoJudgeTaskSession session = executor.openTask(task("Python", source))) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            GoJudgeExecutor.ExecutionResult result = run(session, program, "");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
            assertTrue(result.stdout().length() < 1024);
            assertTrue(client.lastResponse.toString().getBytes(StandardCharsets.UTF_8).length < 8192);
            byte[] expectedChunk = "100000000 ".repeat(1000).getBytes(StandardCharsets.UTF_8);
            try (InputStream output = session.openFile(result.stdoutFileId())) {
                for (int chunk = 0; chunk < 2000; chunk++) {
                    assertArrayEquals(expectedChunk, output.readNBytes(expectedChunk.length));
                }
                assertEquals(-1, output.read());
            }
            assertTrue(OutputComparator.matches(
                    new ByteArrayInputStream("100000000 ".repeat(2000000).getBytes(StandardCharsets.UTF_8)),
                    session.openFile(result.stdoutFileId())));

            SpjProgram spj = new SpjProgram();
            spj.setLanguage("C++");
            spj.setCode("""
                    #include "testlib.h"
                    int main(int argc, char* argv[]) {
                        registerTestlibCmd(argc, argv);
                        int n = inf.readInt(1, 2000000);
                        long long expected = ans.readLong();
                        for (int i = 0; i < n; i++) {
                            if (ouf.readLong() != expected) {
                                quitf(_wa, "unexpected value");
                            }
                        }
                        if (!ouf.seekEof()) {
                            quitf(_wa, "extra output");
                        }
                        quitf(_ok, "accepted");
                    }
                    """);
            spj.setDigest(spj.sourceDigest());
            GoJudgeExecutor.PreparedProgram checker = executor.compileSpj(session, spj);
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.check(session, checker,
                    new ByteArrayInputStream("2000000\n".getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayInputStream("100000000\n".getBytes(StandardCharsets.UTF_8)), result.stdoutFileId()));
            assertEquals(JudgeVerdict.STATUS_WRONG_ANSWER, executor.check(session, checker,
                    new ByteArrayInputStream("2000000\n".getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayInputStream("99999999\n".getBytes(StandardCharsets.UTF_8)), result.stdoutFileId()));
        }
    }

    /**
     * 验证大量标准错误输出不会撑大响应，运行结束立即释放 stderr，任务结束释放 stdout 和产物。
     * @throws Exception 沙箱请求、编译或输出读取失败
     */
    @Test
    void largeStderrKeepsResponseBoundedAndCachedFilesAreReleased() throws Exception {
        String source = "import sys\nsys.stdout.write('ok')\n"
                + "sys.stderr.buffer.write(b'y' * (20 * 1024 * 1024))\n";
        JudgeTask task = task("Python", source);
        String stdoutId;
        String artifactId;
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            artifactId = program.fileId();
            GoJudgeExecutor.ExecutionResult result = run(session, program, "");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
            assertEquals("ok", result.stdout());
            assertTrue(client.lastResponse.toString().getBytes(StandardCharsets.UTF_8).length < 8192);
            String stderrId = client.lastResponse.path("fileIds").path("stderr").asText();
            stdoutId = result.stdoutFileId();
            assertFalse(stderrId.isBlank());
            assertThrows(GoJudgeFileMissingException.class, () -> {
                try (InputStream ignored = session.openFile(stderrId)) {
                    // 如果文件仍然存在，断言失败前关闭响应流。
                }
            });
        }
        for (String fileId : List.of(stdoutId, artifactId)) {
            assertThrows(GoJudgeFileMissingException.class, () -> {
                try (InputStream ignored = client.openFile(fileId, task.getDeadlineEpochMs())) {
                    // 如果文件仍然存在，断言失败前关闭响应流。
                }
            });
        }
    }

    @Test
    void userCannotWriteLauncherDiagnosticsOrTurnExit126IntoInfrastructureFailure() throws Exception {
        String source = "import os\n"
                + "try:\n    os.write(3, b'infrastructure failure')\n    raise AssertionError('inherited descriptor')\n"
                + "except OSError:\n    pass\n"
                + "try:\n    fd = os.open('/proc/%d/fd/3' % os.getppid(), os.O_WRONLY)\n"
                + "    os.close(fd)\n    raise AssertionError('parent descriptor')\n"
                + "except OSError:\n    pass\n"
                + "print('protected', end='')\n";
        try (GoJudgeTaskSession session = executor.openTask(task("Python", source))) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            GoJudgeExecutor.ExecutionResult result = run(session, program, "");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
            assertEquals("protected", result.stdout());
        }
        try (GoJudgeTaskSession session = executor.openTask(task("Python", "raise SystemExit(126)\n"))) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            assertEquals(JudgeVerdict.STATUS_RUNTIME_ERROR, run(session, program, "").status());
        }
    }

    /**
     * 验证容量按原始字节计算，非 UTF-8 输出仍可完整下载且预览使用替换字符。
     * @throws Exception 沙箱请求、编译或输出读取失败
     */
    @Test
    void invalidUtf8WithinRawOutputBudgetDoesNotBecomeRuntimeError() throws Exception {
        String source = "import sys\nsys.stdout.buffer.write(b'\\xff' * 700000)\n";
        try (GoJudgeTaskSession session = executor.openTask(task("Python", source))) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            GoJudgeExecutor.ExecutionResult result = run(session, program, "");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
            assertTrue(result.stdout().length() > 0 && result.stdout().length() < 1024);
            assertTrue(result.stdout().chars().allMatch(character -> character == '\uFFFD'));
            byte[] expected = new byte[700000];
            Arrays.fill(expected, (byte) 0xFF);
            try (InputStream output = session.openFile(result.stdoutFileId())) {
                assertArrayEquals(expected, output.readAllBytes());
            }
        }
    }

    /**
     * 两个 Worker 任务共用单槽沙箱，排队不计入程序墙钟也不被文件传输预算截断
     * @throws Exception 真实沙箱执行或任务回收失败
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "YMCLOUD_GO_JUDGE_TEST_PARALLELISM", matches = "1")
    void queuedExecutionsCompleteBeyondTransferTimeoutAndReleaseTaskSlots() throws Exception {
        properties.setConnectTimeout(Duration.ofMillis(200));
        properties.setRequestTimeout(Duration.ofMillis(500));
        client.close();
        client = new RecordingGoJudgeClient(properties);
        WorkerProperties worker = new WorkerProperties();
        worker.setConcurrency(2);
        executor = createGoJudgeExecutor(client, properties, worker);
        String source = "import time\ntime.sleep(1.5)\nprint('ok', end='')\n";
        try (GoJudgeTaskSession first = executor.openTask(task("Python", source));
             GoJudgeTaskSession second = executor.openTask(task("Python", source))) {
            var threads = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                GoJudgeExecutor.PreparedProgram firstProgram = executor.compile(first);
                GoJudgeExecutor.PreparedProgram secondProgram = executor.compile(second);
                long started = System.nanoTime();
                var firstResult = threads.submit(() -> {
                    start.await();
                    return run(first, firstProgram, "");
                });
                var secondResult = threads.submit(() -> {
                    start.await();
                    return run(second, secondProgram, "");
                });
                start.countDown();
                for (var pending : List.of(firstResult, secondResult)) {
                    GoJudgeExecutor.ExecutionResult result = pending.get(15, TimeUnit.SECONDS);
                    assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
                    assertEquals("ok", result.stdout());
                    assertTrue(result.timeMillis() < 3000);
                }
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 2800,
                        "单槽沙箱应串行执行两个任务，至少一个任务实际等待了执行槽");
            } finally {
                start.countDown();
                threads.shutdownNow();
                assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
        JudgeTask next = task("Python", "print('recovered', end='')");
        next.setDeadlineEpochMs(System.currentTimeMillis() + 5000);
        try (GoJudgeTaskSession first = executor.openTask(next);
             GoJudgeTaskSession second = executor.openTask(next)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(second);
            assertEquals("recovered", run(second, program, "").stdout());
        }
    }

    /**
     * 中断真实 HTTP 执行后释放单槽沙箱及 Worker 任务额度，不等待选手程序自然结束
     * @throws Exception 编译、中断或后续任务执行失败
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "YMCLOUD_GO_JUDGE_TEST_PARALLELISM", matches = "1")
    void interruptedExecutionReleasesSandboxAndWorkerCapacity() throws Exception {
        WorkerProperties worker = new WorkerProperties();
        worker.setConcurrency(1);
        executor = createGoJudgeExecutor(client, properties, worker);
        JudgeTask task = task("Java", javaBody("Thread.sleep(30000);"));
        task.setTimeLimit(60000L);
        AtomicReference<IOException> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            client.executionStarted = new CountDownLatch(1);
            Thread execution = new Thread(() -> {
                try {
                    run(session, program, "");
                } catch (IOException e) {
                    failure.set(e);
                } finally {
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            }, "sandbox-interruption-test");
            execution.start();
            try {
                assertTrue(client.executionStarted.await(5, TimeUnit.SECONDS));
                Thread.sleep(1000);
                assertTrue(execution.isAlive());
                execution.interrupt();
                execution.join(5000);
                assertFalse(execution.isAlive());
                assertNotNull(failure.get());
                assertTrue(interrupted.get());
            } finally {
                execution.interrupt();
                execution.join(5000);
            }
        }
        JudgeTask next = task("Python", "print('recovered', end='')");
        next.setDeadlineEpochMs(System.currentTimeMillis() + 5000);
        try (GoJudgeTaskSession session = executor.openTask(next)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            GoJudgeExecutor.ExecutionResult result = run(session, program, "");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
            assertEquals("recovered", result.stdout());
        }
    }

    private JudgeTask task(String language, String source) {
        JudgeTask task = WorkerFixtures.task(1, 0);
        task.getLanguageConfig().setName(language);
        task.setCode(source);
        task.setTimeLimit(3000L);
        task.setMemoryLimit(256 * 1024L);
        task.setDeadlineEpochMs(System.currentTimeMillis() + 120000);
        return task;
    }

    private GoJudgeExecutor.ExecutionResult run(GoJudgeTaskSession session,
                                                GoJudgeExecutor.PreparedProgram program,
                                                String input) throws IOException {
        return executor.run(session, program, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String echo(String language) {
        return switch (language) {
            case "Java" -> "class Helper { static void echo() throws Exception { System.in.transferTo(System.out); } } "
                    + "public class Main { static class Inner { static void run() throws Exception { Helper.echo(); } } "
                    + "public static void main(String[] a) throws Exception { Inner.run(); } }";
            case "C" -> "#include <stdio.h>\nint main(void) { int c; while ((c=getchar())!=EOF) putchar(c); }";
            case "C++" -> "#include <iostream>\nint main() { std::cout << std::cin.rdbuf(); }";
            default -> "import sys\nsys.stdout.buffer.write(sys.stdin.buffer.read())\n";
        };
    }

    private String busyLoop(String language) {
        return switch (language) {
            case "Java" -> javaBody("while (true) { }");
            case "C", "C++" -> "int main() { for (;;) { } }";
            default -> "while True:\n    pass\n";
        };
    }

    private String sleep(String language) {
        return switch (language) {
            case "Java" -> javaBody("Thread.sleep(10000);");
            case "C", "C++" -> "#include <unistd.h>\nint main() { sleep(10); }";
            default -> "import time\ntime.sleep(10)\n";
        };
    }

    private String memory(String language) {
        return switch (language) {
            case "Java" -> javaBody("var blocks = new java.util.ArrayList<byte[]>(); "
                    + "while (true) { blocks.add(new byte[1024 * 1024]); }");
            case "C", "C++" -> "#include <stdlib.h>\n#include <string.h>\n"
                    + "int main() { for (;;) { volatile char *p=(char*)malloc(1024*1024); "
                    + "if (!p) return 2; for (int i=0;i<1024*1024;i+=4096) p[i]=1; } }";
            default -> "blocks = []\nwhile True:\n    blocks.append(bytearray(1024 * 1024))\n";
        };
    }

    private String crash(String language) {
        return switch (language) {
            case "Java" -> javaBody("throw new IllegalStateException(\"expected\");");
            case "C", "C++" -> "#include <signal.h>\nint main() { raise(SIGSEGV); }";
            default -> "raise RuntimeError('expected')\n";
        };
    }

    /**
     * 构造 stdout 与 stderr 各 20 MiB、合计超过默认输出预算的程序。
     * @param language 受支持的选手语言
     * @return 用固定大小分块写入两个输出通道的选手源码
     */
    private String outputOverflow(String language) {
        return switch (language) {
            case "Java" -> javaBody("byte[] chunk = new byte[65536]; java.util.Arrays.fill(chunk, (byte) 'x'); "
                    + "for (int i = 0; i < 320; i++) System.out.write(chunk); "
                    + "for (int i = 0; i < 320; i++) System.err.write(chunk);");
            case "C", "C++" -> "#include <stdio.h>\n#include <string.h>\n"
                    + "int main() { char chunk[65536]; memset(chunk, 'x', sizeof(chunk)); "
                    + "for(int i=0;i<320;i++) fwrite(chunk,1,sizeof(chunk),stdout); "
                    + "for(int i=0;i<320;i++) fwrite(chunk,1,sizeof(chunk),stderr); }";
            default -> "import sys\nchunk = b'x' * 65536\n"
                    + "for _ in range(320): sys.stdout.buffer.write(chunk)\n"
                    + "for _ in range(320): sys.stderr.buffer.write(chunk)\n";
        };
    }

    private String pollution(String language) {
        return switch (language) {
            case "Java" -> javaBody("var p=java.nio.file.Path.of(\"pollution\"); "
                    + "System.out.print(java.nio.file.Files.exists(p) ? \"dirty\" : \"fresh\"); "
                    + "java.nio.file.Files.writeString(p, \"written\");");
            case "C", "C++" -> "#include <stdio.h>\nint main() { FILE *p=fopen(\"pollution\",\"r\"); "
                    + "printf(p ? \"dirty\" : \"fresh\"); if(p) fclose(p); p=fopen(\"pollution\",\"w\"); "
                    + "if(!p) return 2; fputs(\"written\",p); fclose(p); }";
            default -> "from pathlib import Path\np=Path('pollution')\n"
                    + "print('dirty' if p.exists() else 'fresh', end='')\np.write_text('written')\n";
        };
    }

    private String javaBody(String body) {
        return "public class Main { public static void main(String[] args) throws Exception { " + body + " } }";
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param client 隔离或真实沙箱客户端
     * @param properties 本测试使用的容量、截止或发布配置
     * @param worker 本测试使用的 Worker 资源配置
     * @return 已注入依赖并初始化共享任务额度的执行器
     */
    private static GoJudgeExecutor createGoJudgeExecutor(
            GoJudgeClient client,
            GoJudgeProperties properties,
            WorkerProperties worker) {
        GoJudgeExecutor component = new GoJudgeExecutor();
        ReflectionTestUtils.setField(component, "client", client);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "worker", worker);
        component.initialize();
        return component;
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param client 隔离或真实沙箱客户端
     * @param properties 本测试使用的容量、截止或发布配置
     * @param workerProperties 本测试使用的 Worker 资源配置
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static GoJudgeStartupCheck createGoJudgeStartupCheck(
            GoJudgeClient client,
            GoJudgeProperties properties,
            WorkerProperties workerProperties) {
        GoJudgeStartupCheck component = new GoJudgeStartupCheck();
        ReflectionTestUtils.setField(component, "client", client);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "workerProperties", workerProperties);
        return component;
    }

    /**
     * @Author: Yemiao
     * @CreateTime: 2026-11-07
     * @Description: 保留真实执行响应以检查传输体积及输出文件回收，不替代网络调用。
     */
    private static class RecordingGoJudgeClient extends GoJudgeClient {

        private JsonNode lastResponse;
        private CountDownLatch executionStarted;

        /**
         * 使用集成测试的沙箱连接及容量配置。
         * @param properties 真实沙箱配置
         */
        private RecordingGoJudgeClient(GoJudgeProperties properties) {
            super(properties, new WorkerAmqpConfig().workerObjectMapper());
        }

        /**
         * 标记请求开始并记录真实结果，供中断、容量和文件生命周期断言使用。
         * @param command 固定命令及资源限制
         * @param deadlineEpochMillis 任务截止时间
         * @return 已通过客户端校验的真实响应
         * @throws GoJudgeException 请求失败或响应不符合协议
         */
        @Override
        public List<JsonNode> execute(Map<String, Object> command, long deadlineEpochMillis) throws GoJudgeException {
            if (executionStarted != null) {
                executionStarted.countDown();
            }
            List<JsonNode> responses = super.execute(command, deadlineEpochMillis);
            lastResponse = responses.get(responses.size() - 1);
            return responses;
        }
    }
}
