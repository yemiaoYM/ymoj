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

    @Test
    void concurrencyAndServiceMemoryCannotBeRaisedIndependently() {
        GoJudgeProperties properties = new GoJudgeProperties();
        properties.setToken("test-go-judge-token-0000000000000000000");
        assertDoesNotThrow(properties::validate);
        properties.setSandboxConcurrency(2);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setServiceMemoryBytes(2L * 1024 * 1024 * 1024);
        assertDoesNotThrow(properties::validate);
        GoJudgeStartupCheck check = createGoJudgeStartupCheck(mock(GoJudgeClient.class), properties,
                new WorkerProperties());
        assertThrows(GoJudgeException.class, check::verify);
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
