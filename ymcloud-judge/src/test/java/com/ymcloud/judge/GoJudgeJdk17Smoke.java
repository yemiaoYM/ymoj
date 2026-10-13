package com.ymcloud.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.gojudge.GoJudgeClient;
import com.ymcloud.judge.gojudge.GoJudgeException;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeProperties;
import com.ymcloud.judge.gojudge.GoJudgeStartupCheck;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.pojo.contract.judge.JudgeLanguage;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 独立 main 入口，用真实 JDK 17 验证 HTTP 客户端；不参与默认 JUnit 测试发现。
 * 通过 YMCLOUD_GO_JUDGE_URL 和 YMCLOUD_GO_JUDGE_TOKEN 连接独立的单并发测试服务。
 */
public final class GoJudgeJdk17Smoke {

    private GoJudgeJdk17Smoke() {
    }

    public static void main(String[] arguments) {
        try {
            verify();
            System.out.println("JDK 17 smoke passed: startup, four languages, interruption and bounded recovery");
        } catch (Exception failure) {
            // 禁止打印外部配置、HTTP 请求、令牌或异常正文。
            System.err.println("JDK 17 smoke failed: " + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void verify() throws Exception {
        require(Runtime.version().feature() == 17, "必须使用真实 JDK 17");
        GoJudgeProperties properties = new GoJudgeProperties();
        properties.setBaseUrl(System.getenv().getOrDefault("YMCLOUD_GO_JUDGE_URL", "http://127.0.0.1:5050"));
        properties.setToken(System.getenv("YMCLOUD_GO_JUDGE_TOKEN"));
        WorkerProperties worker = new WorkerProperties();
        try (ObservedClient client = new ObservedClient(properties)) {
            new GoJudgeStartupCheck(client, properties, worker).verify();
            GoJudgeExecutor executor = new GoJudgeExecutor(client, properties, worker);
            for (String language : List.of("Java", "C", "C++", "Python")) {
                JudgeTask task = task(language, echo(language));
                try (GoJudgeTaskSession session = executor.openTask(task)) {
                    GoJudgeExecutor.PreparedProgram program = executor.compile(session);
                    String input = "JDK 17 中文🙂\n";
                    GoJudgeExecutor.ExecutionResult result = executor.run(session, program, input(input));
                    require(result.status() == JudgeVerdict.STATUS_ACCEPTED && input.equals(result.stdout()),
                            "四语言编译运行校验失败");
                }
            }
            verifyInterruption(client, executor);
        }
    }

    private static void verifyInterruption(ObservedClient client, GoJudgeExecutor executor) throws Exception {
        JudgeTask task = task("Java", "public class Main { public static void main(String[] args) "
                + "throws Exception { Thread.sleep(30000); } }");
        task.setTimeLimit(60000L);
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            client.observeNextExecute.set(true);
            Thread execution = new Thread(() -> {
                try {
                    executor.run(session, program, input(""));
                    outcome.set(new IllegalStateException("长任务意外完成"));
                } catch (Throwable failure) {
                    outcome.set(failure);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            }, "jdk17-http-interruption");
            execution.setDaemon(true);
            execution.start();
            require(client.executing.await(5, TimeUnit.SECONDS), "执行请求未发起");
            // 观察点位于上传之后的 execute 入口，给独占服务留出实际启动长任务的时间。
            Thread.sleep(1000L);
            execution.interrupt();
            execution.join(6000L);
            require(!execution.isAlive() && outcome.get() instanceof IOException && interrupted.get(),
                    "JDK 17 中断未在边界内取消请求");

            long started = System.nanoTime();
            Map<String, Object> command = Map.ofEntries(
                    Map.entry("args", List.of("/usr/local/bin/ymcloud-output-limit", "65536", "16777216",
                            "/usr/bin/printf", "recovered")),
                    Map.entry("env", List.of("PATH=/usr/local/bin:/usr/bin:/bin", "LANG=C.UTF-8")),
                    Map.entry("files", List.of(Map.of("content", ""),
                            Map.of("name", "stdout", "max", 32768, "pipe", true),
                            Map.of("name", "stderr", "max", 32768, "pipe", true),
                            Map.of("name", "launcher", "max", 256, "pipe", true))),
                    Map.entry("cpuLimit", TimeUnit.SECONDS.toNanos(2)),
                    Map.entry("clockLimit", TimeUnit.SECONDS.toNanos(2)),
                    Map.entry("memoryLimit", 64L * 1024 * 1024),
                    Map.entry("procLimit", 64), Map.entry("cpuRateLimit", 1000));
            JsonNode recovered = client.execute(Map.of("cmd", List.of(command)),
                    System.currentTimeMillis() + 4000L).get(0);
            require("Accepted".equals(recovered.path("status").asText())
                            && "recovered".equals(recovered.path("files").path("stdout").asText())
                            && recovered.path("files").path("launcher").isTextual()
                            && recovered.path("files").path("launcher").textValue().isEmpty()
                            && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000L,
                    "取消后单并发沙箱未及时恢复");
        }
    }

    private static JudgeTask task(String language, String source) {
        JudgeLanguage configuration = new JudgeLanguage();
        configuration.setName(language);
        return JudgeTask.builder().executionId(UUID.randomUUID().toString()).submissionId(1L)
                .testDataVersionId(77L).languageConfig(configuration).code(source).timeLimit(5000L)
                .memoryLimit(256 * 1024L).deadlineEpochMs(System.currentTimeMillis() + 120000L).build();
    }

    private static String echo(String language) {
        return switch (language) {
            case "Java" -> "public class Main { public static void main(String[] args) throws Exception "
                    + "{ System.in.transferTo(System.out); } }";
            case "C", "C++" -> "#include <stdio.h>\nint main(void) { int c; "
                    + "while ((c = getchar()) != EOF) putchar(c); return 0; }";
            default -> "import sys\nsys.stdout.buffer.write(sys.stdin.buffer.read())\n";
        };
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class ObservedClient extends GoJudgeClient {

        private final CountDownLatch executing = new CountDownLatch(1);
        private final AtomicBoolean observeNextExecute = new AtomicBoolean();

        private ObservedClient(GoJudgeProperties properties) {
            super(properties, new ObjectMapper());
        }

        @Override
        public List<JsonNode> execute(Map<String, Object> command, long deadlineEpochMillis) throws GoJudgeException {
            if (observeNextExecute.compareAndSet(true, false)) {
                executing.countDown();
            }
            return super.execute(command, deadlineEpochMillis);
        }
    }
}
