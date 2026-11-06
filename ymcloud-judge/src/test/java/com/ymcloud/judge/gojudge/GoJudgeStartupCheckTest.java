package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ymcloud.judge.config.WorkerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 启动门禁不允许版本偏离、无鉴权、cgroup 回退或不完整的工具链继续消费任务。
 */
class GoJudgeStartupCheckTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VERSION = "{\"buildVersion\":\"v1.13.3\",\"os\":\"linux\","
            + "\"fixSymlinkEscape\":true,\"copyOutTruncate\":true}";
    private static final String CONFIG = "{\"fileStorePath\":\"/var/lib/go-judge/files\",\"runnerConfig\":{"
            + "\"cgroupType\":2,\"seccomp\":true,\"workDir\":\"/w\","
            + "\"cgroupControllers\":[\"cpu\",\"memory\",\"pids\"]}}";

    @Test
    void fixedVersionAndCgroupV2AreMandatory() throws Exception {
        assertDoesNotThrow(() -> GoJudgeStartupCheck.validateVersion(MAPPER.readTree(VERSION)));
        assertDoesNotThrow(() -> GoJudgeStartupCheck.validateConfig(MAPPER.readTree(CONFIG)));
        for (String invalid : List.of(VERSION.replace("v1.13.3", "v1.13.2"),
                VERSION.replace("linux", "windows"), VERSION.replace("true", "false"))) {
            assertThrows(GoJudgeException.class,
                    () -> GoJudgeStartupCheck.validateVersion(MAPPER.readTree(invalid)));
        }
        for (String invalid : List.of(CONFIG.replace("\"cgroupType\":2", "\"cgroupType\":0"),
                CONFIG.replace("\"cgroupType\":2", "\"cgroupType\":1"),
                CONFIG.replace("\"seccomp\":true", "\"seccomp\":false"),
                CONFIG.replace("\"pids\"", "\"missing\""),
                CONFIG.replace("/var/lib/go-judge/files", "/dev/shm/go-judge"),
                CONFIG.replace("/w", "/app"))) {
            assertThrows(GoJudgeException.class,
                    () -> GoJudgeStartupCheck.validateConfig(MAPPER.readTree(invalid)));
        }
    }

    @Test
    void successfulStartupVerifiesAuthenticationAndEveryToolchainInSandbox() throws Exception {
        GoJudgeClient client = readyClient();
        GoJudgeStartupCheck check = check(client);
        assertDoesNotThrow(check::verify);
        verify(client).verifyAuthentication(anyLong());
        verify(client).version(anyLong());
        verify(client).config(anyLong());
        verify(client, times(8)).execute(anyMap(), anyLong());
    }

    @Test
    void invalidTokenStopsBeforeExecutingAnyProbe() throws Exception {
        GoJudgeClient client = readyClient();
        doThrow(new GoJudgeException("go-judge HTTP 状态异常：401"))
                .when(client).verifyAuthentication(anyLong());
        assertThrows(GoJudgeException.class, () -> check(client).verify());
        verify(client, never()).execute(anyMap(), anyLong());
    }

    @Test
    void incompatibleCompilerStopsStartup() throws Exception {
        GoJudgeClient client = readyClient();
        doReturn(List.of(response("javac 21.0.1"))).when(client).execute(anyMap(), anyLong());
        assertThrows(GoJudgeException.class, () -> check(client).verify());
    }

    /**
     * 旧版输出限制程序须阻止消费，并明确提示升级沙箱而非仅重启 Worker。
     * @throws Exception 探针响应构造失败
     */
    @Test
    void oldLauncherReportsExpectedAndActualVersionWithUpgradeAction() throws Exception {
        GoJudgeClient client = readyClient();
        doReturn(List.of(response("openjdk version \"17.0.19\"")),
                 List.of(response("javac 17.0.19")),
                 List.of(response("12.2.0")),
                 List.of(response("12.2.0")),
                 List.of(response("Python 3.11.2")),
                 List.of(response("ymcloud-output-limit 1")))
                .when(client).execute(anyMap(), anyLong());

        GoJudgeException exception = assertThrows(GoJudgeException.class, () -> check(client).verify());

        assertTrue(exception.getMessage().contains("预期匹配：ymcloud-output-limit 2"));
        assertTrue(exception.getMessage().contains("实际输出：ymcloud-output-limit 1"));
        assertTrue(exception.getMessage().contains("重新构建 go-judge 镜像并重建容器"));
        verify(client, times(6)).execute(anyMap(), anyLong());
    }

    @Test
    void launcherDiagnosticMustBePresentAndEmptyBeforeConsumptionStarts() throws Exception {
        for (String diagnostic : List.of("FORK\n", "invalid")) {
            GoJudgeClient client = readyClient();
            ObjectNode result = (ObjectNode) response("openjdk version \"17.0.19\"");
            ((ObjectNode) result.get("files")).put("launcher", diagnostic);
            doReturn(List.of(result)).when(client).execute(anyMap(), anyLong());
            assertThrows(GoJudgeException.class, () -> check(client).verify());
            ((ObjectNode) result.get("files")).remove("launcher");
            assertThrows(GoJudgeException.class, () -> check(client).verify());
        }
    }

    /**
     * 1024 MiB 单题预算须有服务预留空间，提高沙箱并发时仍要求配套扩容
     * @throws Exception 探针响应构造失败
     */
    @Test
    void concurrencyAndServiceMemoryCannotBeRaisedIndependently() throws Exception {
        GoJudgeProperties properties = new GoJudgeProperties();
        properties.setSandboxConcurrency(1);
        properties.setToken("test-go-judge-token-0000000000000000000");
        assertDoesNotThrow(properties::validate);
        properties.setSandboxConcurrency(2);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setServiceMemoryBytes(2L * 1024 * 1024 * 1024);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setServiceMemoryBytes(3L * 1024 * 1024 * 1024);
        assertDoesNotThrow(properties::validate);
        GoJudgeStartupCheck check = createGoJudgeStartupCheck(readyClient(), properties,
                new WorkerProperties());
        assertDoesNotThrow(check::verify);
    }

    /**
     * Worker 默认使用自身 CPU 加 1，不随独立沙箱容量变化或受 32 核硬上限影响
     * @throws Exception 探针响应构造失败
     */
    @Test
    void defaultTaskConcurrencyUsesWorkerCpuIndependentlyOfSandboxCapacity() throws Exception {
        for (int concurrency : List.of(1, 2, 64)) {
            GoJudgeProperties properties = new GoJudgeProperties();
            properties.setSandboxConcurrency(concurrency);
            properties.setServiceMemoryBytes((concurrency + 1L) * 2 * 1024 * 1024 * 1024);
            properties.setToken("test-go-judge-token-0000000000000000000");
            properties.validate();
            WorkerProperties worker = new WorkerProperties();

            createGoJudgeStartupCheck(readyClient(), properties, worker).verify();

            assertEquals(Runtime.getRuntime().availableProcessors() + 1, worker.getConcurrency());
            assertDoesNotThrow(worker::validate);
            worker.setConcurrency(concurrency + 1);
            assertDoesNotThrow(worker::validate);
        }
    }

    /**
     * 管理员指定任务并发时保持其值，不要求与沙箱执行槽位相等
     * @throws Exception 探针响应构造失败
     */
    @Test
    void explicitTaskConcurrencyRemainsIndependentOfSandboxSlots() throws Exception {
        GoJudgeProperties properties = new GoJudgeProperties();
        properties.setSandboxConcurrency(1);
        WorkerProperties worker = new WorkerProperties();
        worker.setConcurrency(4);

        createGoJudgeStartupCheck(readyClient(), properties, worker).verify();

        assertEquals(4, worker.getConcurrency());
    }

    /**
     * 输出文件与上传预算必须配套，缓存文件传输不要求响应 JSON 随输出上限扩大
     * @throws Exception 探针响应构造失败
     */
    @Test
    void cachedOutputRequiresFileAndUploadCapacityWithoutExpandingJson() throws Exception {
        GoJudgeClient client = readyClient();
        GoJudgeProperties properties = new GoJudgeProperties();
        WorkerProperties worker = new WorkerProperties();
        worker.setMaxOutputBytes(128 * 1024 * 1024);
        properties.setMaxFileBytes(worker.getMaxOutputBytes());
        properties.setMaxRequestBytes(129 * 1024 * 1024);
        assertDoesNotThrow(worker::validate);
        assertDoesNotThrow(createGoJudgeStartupCheck(client, properties, worker)::verify);

        properties.setMaxFileBytes(worker.getMaxOutputBytes() - 1L);
        assertThrows(GoJudgeException.class,
                createGoJudgeStartupCheck(client, properties, worker)::verify);
        properties.setMaxFileBytes(worker.getMaxOutputBytes());
        properties.setMaxRequestBytes(129 * 1024 * 1024 - 1);
        assertThrows(GoJudgeException.class,
                createGoJudgeStartupCheck(client, properties, worker)::verify);

        worker.setMaxOutputBytes(1023);
        assertThrows(IllegalArgumentException.class, worker::validate);
    }

    private static GoJudgeStartupCheck check(GoJudgeClient client) {
        return createGoJudgeStartupCheck(client, new GoJudgeProperties(), new WorkerProperties());
    }

    private static GoJudgeClient readyClient() throws Exception {
        GoJudgeClient client = mock(GoJudgeClient.class);
        when(client.version(anyLong())).thenReturn(MAPPER.readTree(VERSION));
        when(client.config(anyLong())).thenReturn(MAPPER.readTree(CONFIG));
        when(client.execute(anyMap(), anyLong())).thenAnswer(invocation -> {
            Map<?, ?> request = invocation.getArgument(0);
            Map<?, ?> command = (Map<?, ?>) ((List<?>) request.get("cmd")).get(0);
            assertEquals(Map.of("name", "launcher", "max", 256, "pipe", true),
                    ((List<?>) command.get("files")).get(3));
            List<?> args = (List<?>) command.get("args");
            String output = switch (args.get(0).toString()) {
                case "/usr/bin/java" -> "openjdk version \"17.0.19\" 2026-04-21\nOpenJDK Runtime Environment";
                case "/usr/bin/javac" -> "javac 17.0.19";
                case "/usr/bin/gcc", "/usr/bin/g++" -> "12.2.0";
                case "/usr/local/bin/ymcloud-output-limit" -> args.contains("--version")
                        ? "ymcloud-output-limit 2" : "launcher-ready";
                case "/usr/bin/python3" -> args.contains("--version") ? "Python 3.11.2" : "sandbox-ready";
                default -> throw new AssertionError("未知启动探测命令");
            };
            return List.of(response(output));
        });
        return client;
    }

    private static JsonNode response(String output) {
        return MAPPER.valueToTree(Map.of("status", "Accepted", "exitStatus", 0,
                "files", Map.of("stdout", output, "stderr", "", "launcher", "")));
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
}
