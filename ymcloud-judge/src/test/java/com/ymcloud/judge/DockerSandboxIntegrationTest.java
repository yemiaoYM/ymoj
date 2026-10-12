package com.ymcloud.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.ymcloud.judge.bo.CodeRunResult;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.exception.MemoryLimitExceededException;
import com.ymcloud.judge.exception.RuntimeErrorException;
import com.ymcloud.judge.exception.TimeLimitExceededException;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定可信程序的真实 Docker 回归，使用独立客户端和临时目录，不连接业务服务。
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_RUN_SANDBOX_TESTS", matches = "true")
class DockerSandboxIntegrationTest {

    private static final String INPUT = " \t中文😀\r\ntrailing  \n\n";

    @TempDir
    private Path workingDir;

    @ParameterizedTest
    @ValueSource(strings = {"Java", "C", "C++", "Python"})
    void trustedProgramsPreserveUtf8WhitespaceAndReportCompileAndRuntimeErrors(String language) throws Exception {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        try (var transport = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(30))
                .build();
             DockerClient docker = DockerClientImpl.getInstance(config, transport)) {
            docker.pingCmd().exec();
            String image = switch (language) {
                case "Java" -> "oj-java-env";
                case "C", "C++" -> "oj-cpp-env";
                case "Python" -> "oj-python-env";
                default -> throw new IllegalArgumentException("未知测试语言");
            };
            CodeRunResult result = null;
            // 与正式任务一致：每次编译使用独立容器，避免编译器残留影响下一任务。
            for (String scenario : new String[]{"echo", "compile-error", "runtime-error", "time-limit", "output-limit", "memory-limit"}) {
                Path taskDirectory = Files.createDirectory(workingDir.resolve(scenario));
                CodeExecContainer container = new CodeExecContainerFactory(
                        docker, taskDirectory.toString(), "/app", scenario.equals("memory-limit") ? 128L * 1024 : 1024L * 1024, image)
                        .createDockerContainer();
                try {
                    container.start();
                    SandBox sandbox = new SandBoxFactory(language).createSandBox(container);
                    if (scenario.equals("echo")) {
                        sandbox.saveCode(echoProgram(language));
                        sandbox.compile(container);
                        result = sandbox.execute(container, INPUT, 2000L);
                        assertEquals(INPUT, result.getStdout(), "UTF-8 和首尾空白必须原样传递");
                        assertTrue(result.getTimeUsed() >= 0);
                        assertTrue(result.getMemoryUsed() >= 0);
                        assertEquals("", sandbox.execute(container, "", 2000L).getStdout(), "空输入也必须立即收到 EOF");
                    } else if (scenario.equals("compile-error")) {
                        sandbox.saveCode(invalidProgram(language));
                        assertThrows(CompileException.class, () -> sandbox.compile(container));
                    } else if (scenario.equals("runtime-error")) {
                        sandbox.saveCode(exitProgram(language));
                        sandbox.compile(container);
                        assertThrows(RuntimeErrorException.class, () -> sandbox.execute(container, "", 2000L));
                    } else if (scenario.equals("time-limit")) {
                        sandbox.saveCode(loopProgram(language));
                        sandbox.compile(container);
                        assertThrows(TimeLimitExceededException.class, () -> sandbox.execute(container, "", 300L));
                    } else if (scenario.equals("output-limit")) {
                        sandbox.saveCode(outputProgram(language));
                        sandbox.compile(container);
                        container.setMaxOutputBytes(4096);
                        assertThrows(RuntimeErrorException.class, () -> sandbox.execute(container, "", 2000L));
                    } else {
                        sandbox.saveCode(memoryProgram(language));
                        sandbox.compile(container);
                        assertThrows(MemoryLimitExceededException.class, () -> sandbox.execute(container, "", 5000L));
                    }
                } finally {
                    // 只清理本测试创建且持有 ID 的容器；临时目录由 JUnit 清理。
                    container.remove(true);
                }
            }
            System.out.printf("SANDBOX_VERIFIED language=%s utf8Whitespace=PASS stdinEof=PASS emptyInput=PASS compileError=PASS runtimeError=PASS timeMs=%d memoryKb=%d%n",
                    language, result.getTimeUsed(), result.getMemoryUsed());
        }
    }

    private String echoProgram(String language) {
        return switch (language) {
            case "Java" -> "public class Main { public static void main(String[] a) throws Exception { System.in.transferTo(System.out); } }";
            case "C" -> "#include <stdio.h>\nint main(void) { int c; while ((c = getchar()) != EOF) { putchar(c); } return 0; }";
            case "C++" -> "#include <iostream>\nint main() { std::cout << std::cin.rdbuf(); return 0; }";
            case "Python" -> "import sys\nsys.stdout.buffer.write(sys.stdin.buffer.read())\n";
            default -> throw new IllegalArgumentException("未知测试语言");
        };
    }

    private String invalidProgram(String language) {
        return switch (language) {
            case "Java" -> "public class Main { invalid syntax }";
            case "C", "C++" -> "int main( { invalid syntax }";
            case "Python" -> "if True print('invalid')\n";
            default -> throw new IllegalArgumentException("未知测试语言");
        };
    }

    private String exitProgram(String language) {
        return switch (language) {
            case "Java" -> "public class Main { public static void main(String[] a) { System.exit(3); } }";
            case "C", "C++" -> "int main(void) { return 3; }";
            case "Python" -> "raise SystemExit(3)\n";
            default -> throw new IllegalArgumentException("未知测试语言");
        };
    }

    private String loopProgram(String language) {
        return switch (language) {
            case "Java" -> "public class Main { public static void main(String[] a) { while (true) {} } }";
            case "C", "C++" -> "int main(void) { while(1) {} }";
            case "Python" -> "while True: pass\n";
            default -> throw new IllegalArgumentException("未知测试语言");
        };
    }

    private String outputProgram(String language) {
        return switch (language) {
            case "Java" -> "public class Main { public static void main(String[] a) { while (true) { System.out.println(123456789); } } }";
            case "C", "C++" -> "#include <stdio.h>\nint main(void) { while(1) { puts(\"123456789\"); } }";
            case "Python" -> "while True: print('123456789', flush=True)\n";
            default -> throw new IllegalArgumentException("未知测试语言");
        };
    }

    private String memoryProgram(String language) {
        return switch (language) {
            case "Java" -> "import java.util.*; public class Main { public static void main(String[] a) { List<byte[]> blocks = new ArrayList<>(); while(true) { blocks.add(new byte[1024*1024]); } } }";
            case "C", "C++" -> "#include <stdlib.h>\nint main(void) { while(1) { volatile char *p = (char*)malloc(1024*1024); if(!p) return 3; for(int i=0;i<1024*1024;i+=4096) p[i]=1; } }";
            case "Python" -> "blocks=[]\nwhile True: blocks.append(bytearray(1024*1024))\n";
            default -> throw new IllegalArgumentException("未知测试语言");
        };
    }
}
