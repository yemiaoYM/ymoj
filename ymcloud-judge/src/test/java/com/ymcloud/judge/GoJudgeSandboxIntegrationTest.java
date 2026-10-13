package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.gojudge.GoJudgeClient;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeProperties;
import com.ymcloud.judge.gojudge.GoJudgeStartupCheck;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用独立的真实 go-judge 验证四语言入口；不会管理 Docker 或使用业务服务。
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_RUN_GO_JUDGE_TESTS", matches = "true")
class GoJudgeSandboxIntegrationTest {

    private GoJudgeClient client;
    private GoJudgeExecutor executor;
    private GoJudgeProperties properties;

    @BeforeEach
    void setup() {
        properties = new GoJudgeProperties();
        properties.setBaseUrl(System.getenv().getOrDefault("YMCLOUD_GO_JUDGE_URL", "http://127.0.0.1:5050"));
        properties.setToken(System.getenv("YMCLOUD_GO_JUDGE_TOKEN"));
        client = new GoJudgeClient(properties, new WorkerAmqpConfig().workerObjectMapper());
        executor = new GoJudgeExecutor(client, properties, new WorkerProperties());
    }

    @Test
    void deployedServicePassesMandatoryStartupChecks() throws Exception {
        new GoJudgeStartupCheck(client, properties, new WorkerProperties()).verify();
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

    @Test
    void stdoutCanUseMoreThanHalfOfSharedBudgetAndPrintedMarkersCannotChangeVerdict() throws Exception {
        String source = "import sys\nsys.stdout.write('x' * 700000)\n"
                + "sys.stderr.write('java.lang.OutOfMemoryError __YMCLOUD_TIME__=999999')\n";
        try (GoJudgeTaskSession session = executor.openTask(task("Python", source))) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            GoJudgeExecutor.ExecutionResult result = run(session, program, "");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
            assertEquals(700000, result.stdout().length());
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

    @Test
    void invalidUtf8WithinRawOutputBudgetDoesNotBecomeRuntimeError() throws Exception {
        String source = "import sys\nsys.stdout.buffer.write(b'\\xff' * 700000)\n";
        try (GoJudgeTaskSession session = executor.openTask(task("Python", source))) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            GoJudgeExecutor.ExecutionResult result = run(session, program, "");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.status());
            assertEquals(700000, result.stdout().length());
            assertTrue(result.stdout().chars().allMatch(character -> character == '\uFFFD'));
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

    private String outputOverflow(String language) {
        return switch (language) {
            case "Java" -> javaBody("System.out.print(\"x\".repeat(700000)); System.err.print(\"y\".repeat(700000));");
            case "C", "C++" -> "#include <stdio.h>\nint main() { for(int i=0;i<700000;i++) putchar('x'); "
                    + "for(int i=0;i<700000;i++) fputc('y',stderr); }";
            default -> "import sys\nsys.stdout.write('x' * 700000)\nsys.stderr.write('y' * 700000)\n";
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
}
