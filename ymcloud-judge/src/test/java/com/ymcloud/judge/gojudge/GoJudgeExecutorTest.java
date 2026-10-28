package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.pojo.contract.judge.JudgeLanguage;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 验证沙箱命令、结构化判定、资源边界及文件回收。
 */
class GoJudgeExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private GoJudgeClient client;
    private GoJudgeExecutor executor;
    private WorkerProperties worker;
    private JudgeTask task;

    @BeforeEach
    void setup() {
        client = mock(GoJudgeClient.class);
        worker = new WorkerProperties();
        executor = createGoJudgeExecutor(client, new GoJudgeProperties(), worker);
        JudgeLanguage language = new JudgeLanguage();
        language.setName("Java");
        language.setCompileCmd("untrusted legacy command");
        language.setRunCmd("untrusted legacy command");
        task = JudgeTask.builder().executionId("test").languageConfig(language).code("class Main {}")
                .timeLimit(1000L).memoryLimit(262144L).deadlineEpochMs(System.currentTimeMillis() + 60000).build();
    }

    /**
     * 1024 MiB 题目通过容量检查，并以完整字节预算运行；超出上限仍拒绝。
     * @throws Exception 沙箱模拟调用失败
     */
    @Test
    void supportsOneGiBWithoutTruncatingRuntimeBudget() throws Exception {
        task.setMemoryLimit(1048576L);
        assertNull(executor.capacityFailure(task));
        when(client.upload(any(InputStream.class), eq("stdin"), anyLong())).thenReturn("input-id");
        ObjectNode response = result("Accepted");
        response.put("memory", 1073741824L);
        when(client.execute(any(), anyLong())).thenAnswer(invocation -> {
            Map<?, ?> request = invocation.getArgument(0);
            Map<?, ?> command = (Map<?, ?>) ((List<?>) request.get("cmd")).get(0);
            assertEquals(1073741824L, command.get("memoryLimit"));
            assertTrue(command.get("args").toString().contains("-Xmx1048576k"));
            return List.of(response);
        });
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            var program = new GoJudgeExecutor.PreparedProgram("Java", "program.jar", "artifact-id");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.run(session, program, stream("1")).status());
        }
        response.put("memory", 1073741825L);
        assertEquals(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED, executor.classify(response, task).status());
        task.setMemoryLimit(1048577L);
        assertNotNull(executor.capacityFailure(task));
    }

    @Test
    void wallClockControlsVerdictAndStatisticsUseMillisecondsAndKiB() throws Exception {
        ObjectNode response = result("Accepted");
        response.put("time", 1L);
        response.put("runTime", 1_000_000_001L);
        response.put("memory", 2049L);
        GoJudgeExecutor.ExecutionResult run = executor.classify(response, task);
        assertEquals(JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED, run.status());
        assertEquals(1001, run.timeMillis());
        assertEquals(3, run.memoryKiB());
        response.put("runTime", 1_000_000_000L);
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.classify(response, task).status());
    }

    @Test
    void structuredStatusesCannotBeOverriddenByPrintedExceptionOrResourceMarkers() throws Exception {
        Map<String, JudgeVerdict> mapping = Map.of(
                "Accepted", JudgeVerdict.STATUS_ACCEPTED,
                "Time Limit Exceeded", JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED,
                "Memory Limit Exceeded", JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED,
                "Nonzero Exit Status", JudgeVerdict.STATUS_RUNTIME_ERROR,
                "Signalled", JudgeVerdict.STATUS_RUNTIME_ERROR,
                "Output Limit Exceeded", JudgeVerdict.STATUS_RUNTIME_ERROR);
        for (var entry : mapping.entrySet()) {
            ObjectNode response = result(entry.getKey());
            response.putObject("files").put("stdout", "__TIME__:999999 999999")
                    .put("stderr", "java.lang.OutOfMemoryError\n__TIME__:0 0").put("launcher", "");
            assertEquals(entry.getValue(), executor.classify(response, task).status());
        }
        for (String status : List.of("Internal Error", "New Unknown Status", "File Error")) {
            assertThrows(IOException.class, () -> executor.classify(result(status), task));
        }
    }

    @Test
    void malformedResourcesDoNotOverflowOrBecomeAccepted() throws Exception {
        for (String field : List.of("memory", "runTime")) {
            ObjectNode response = result("Accepted");
            response.put(field, -1L);
            assertThrows(IOException.class, () -> executor.classify(response, task));
            response.put(field, "9999999999999999999999999999999");
            assertThrows(IOException.class, () -> executor.classify(response, task));
            response.remove(field);
            assertThrows(IOException.class, () -> executor.classify(response, task));
        }
        ObjectNode missingOutput = result("Accepted");
        missingOutput.remove("files");
        assertThrows(IOException.class, () -> executor.classify(missingOutput, task));
    }

    @Test
    void decodedReplacementCharactersCannotInflateTheRawLauncherOutputBudget() throws Exception {
        ObjectNode response = result("Accepted");
        response.putObject("files").put("stdout", "\uFFFD".repeat(700000))
                .put("stderr", "").put("launcher", "");
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.classify(response, task).status());
    }

    @Test
    void userExit126RemainsReWhileTrustedLauncherFailuresBecomeSystemFailures() throws Exception {
        ObjectNode response = result("Nonzero Exit Status");
        response.put("exitStatus", 126);
        assertEquals(JudgeVerdict.STATUS_RUNTIME_ERROR, executor.classify(response, task).status());
        for (String diagnostic : List.of("FORK\n", "EXEC\n", "unknown fault")) {
            ((ObjectNode) response.get("files")).put("launcher", diagnostic);
            assertThrows(IOException.class, () -> executor.classify(response, task));
        }
        ((ObjectNode) response.get("files")).remove("launcher");
        assertThrows(IOException.class, () -> executor.classify(response, task));
        ((ObjectNode) response.get("files")).put("launcher", 126);
        assertThrows(IOException.class, () -> executor.classify(response, task));
    }

    @Test
    void compilerLauncherFailureCannotBeReportedAsUserCompileError() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("source-id");
        ObjectNode response = result("Nonzero Exit Status");
        response.put("exitStatus", 126);
        ((ObjectNode) response.get("files")).put("launcher", "EXEC\n");
        when(client.execute(any(), anyLong())).thenReturn(List.of(response));
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            assertThrows(IOException.class, () -> executor.compile(session));
        }
        verify(client).delete(eq("source-id"), anyLong());
    }

    @Test
    void fourEntrypointsUseFixedCommandsAndCacheCompleteArtifacts() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("source-id");
        for (String language : List.of("Java", "C", "C++", "Python")) {
            task.getLanguageConfig().setName(language);
            String artifact = language.equals("Java") ? "program.jar" : language.equals("Python") ? "main.py" : "program";
            ObjectNode response = result("Accepted");
            response.putObject("fileIds").put(artifact, "artifact-id");
            doAnswer(invocation -> {
                Map<?, ?> request = invocation.getArgument(0);
                Map<?, ?> command = (Map<?, ?>) ((List<?>) request.get("cmd")).get(0);
                String arguments = command.get("args").toString();
                assertFalse(arguments.contains("untrusted legacy"));
                assertTrue(arguments.startsWith("[/usr/local/bin/ymcloud-output-limit, 1048576, 16777216,"));
                assertEquals(512L * 1024 * 1024, command.get("memoryLimit"));
                assertEquals(10_000_000_000L, command.get("clockLimit"));
                assertEquals(false, command.get("copyOutTruncate"));
                assertEquals(List.of(artifact + "?"), command.get("copyOutCached"));
                assertEquals(Map.of("name", "launcher", "max", 256, "pipe", true),
                        ((List<?>) command.get("files")).get(3));
                if (language.equals("Java")) {
                    assertTrue(arguments.contains("-C classes ."));
                    assertTrue(arguments.contains("-J-Djava.io.tmpdir=/w -d classes Main.java"));
                    assertTrue(arguments.contains("/usr/bin/jar -J-Xmx128m -J-Djava.io.tmpdir=/w"));
                } else if (language.equals("C")) {
                    assertTrue(arguments.contains("-std=c11"));
                } else if (language.equals("C++")) {
                    assertTrue(arguments.contains("-std=c++17"));
                } else {
                    assertTrue(arguments.contains("py_compile"));
                }
                return List.of(response);
            }).when(client).execute(any(), anyLong());
            try (GoJudgeTaskSession session = executor.openTask(task)) {
                assertEquals(artifact, executor.compile(session).artifact());
            }
        }
        verify(client, times(4)).delete(eq("source-id"), anyLong());
        verify(client, times(4)).delete(eq("artifact-id"), anyLong());
    }

    @Test
    void compilationResourceFailuresAreCeWhileFileLossAndInternalErrorsRemainSystemFailures() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("source-id");
        for (String status : List.of("Nonzero Exit Status", "Time Limit Exceeded", "Memory Limit Exceeded",
                "Output Limit Exceeded", "Signalled")) {
            ObjectNode response = result(status);
            response.putObject("fileIds").put("program.jar", "partial-artifact");
            when(client.execute(any(), anyLong())).thenReturn(List.of(response));
            try (GoJudgeTaskSession session = executor.openTask(task)) {
                assertThrows(CompileException.class, () -> executor.compile(session));
            }
        }
        verify(client, times(5)).delete(eq("partial-artifact"), anyLong());
        ObjectNode tooLarge = result("File Error");
        tooLarge.putArray("fileError").addObject().put("type", "CopyOutSizeExceeded");
        when(client.execute(any(), anyLong())).thenReturn(List.of(tooLarge));
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            assertThrows(CompileException.class, () -> executor.compile(session));
        }
        when(client.execute(any(), anyLong())).thenThrow(new GoJudgeFileMissingException());
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            assertThrows(GoJudgeFileMissingException.class, () -> executor.compile(session));
        }
    }

    @Test
    void compilerSuccessAfterResourceBoundaryIsStillCeAndCachedArtifactsAreDeleted() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("source-id");
        for (String field : List.of("runTime", "memory")) {
            ObjectNode response = result("Accepted");
            response.putObject("fileIds").put("program.jar", "artifact-id");
            response.put(field, field.equals("runTime") ? 10_000_000_001L : 512L * 1024 * 1024 + 1);
            when(client.execute(any(), anyLong())).thenReturn(List.of(response));
            try (GoJudgeTaskSession session = executor.openTask(task)) {
                assertThrows(CompileException.class, () -> executor.compile(session));
            }
        }
        verify(client, times(2)).delete(eq("artifact-id"), anyLong());
    }

    @Test
    void everyPointGetsFreshInputAndFreshSandboxWhileFixedArtifactIsReused() throws Exception {
        when(client.upload(any(InputStream.class), eq("stdin"), anyLong())).thenReturn("input-one", "input-two");
        when(client.execute(any(), anyLong())).thenAnswer(invocation -> {
            Map<?, ?> request = invocation.getArgument(0);
            Map<?, ?> command = (Map<?, ?>) ((List<?>) request.get("cmd")).get(0);
            assertEquals(Map.of("program.jar", Map.of("fileId", "artifact-id")), command.get("copyIn"));
            assertFalse(command.containsKey("copyOutCached"));
            return List.of(result("Accepted"));
        });
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = new GoJudgeExecutor.PreparedProgram("Java", "program.jar", "artifact-id");
            executor.run(session, program, stream("first"));
            verify(client).delete(eq("input-one"), anyLong());
            executor.run(session, program, stream("second"));
            verify(client).delete(eq("input-two"), anyLong());
        }
    }

    @Test
    void cpuProtectionCoversParallelExecutionWhileBusinessWallBoundaryRemainsExact() throws Exception {
        GoJudgeProperties properties = new GoJudgeProperties();
        properties.setCpuRateLimit(4000);
        GoJudgeExecutor parallel = createGoJudgeExecutor(client, properties, worker);
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("input-id");
        when(client.execute(any(), anyLong())).thenAnswer(invocation -> {
            Map<?, ?> request = invocation.getArgument(0);
            Map<?, ?> command = (Map<?, ?>) ((List<?>) request.get("cmd")).get(0);
            assertEquals(1_000_000_000L, command.get("clockLimit"));
            assertEquals(4_400_000_000L, command.get("cpuLimit"));
            assertEquals(4000, command.get("cpuRateLimit"));
            ObjectNode response = result("Accepted");
            response.put("runTime", 900_000_000L);
            response.put("time", 3_000_000_000L);
            return List.of(response);
        });
        try (GoJudgeTaskSession session = parallel.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = new GoJudgeExecutor.PreparedProgram("Java", "program.jar", "artifact-id");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, parallel.run(session, program, stream("")).status());
        }
    }

    @Test
    void failedInputCleanupIsRetriedAtTaskEndAndInterruptIsPreserved() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("input-id");
        doThrow(new GoJudgeException("offline")).doNothing().when(client).delete(eq("input-id"), anyLong());
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            String id = session.upload(stream(""), "stdin");
            Thread.currentThread().interrupt();
            try {
                session.release(id);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
        verify(client, times(2)).delete(eq("input-id"), anyLong());
    }

    @Test
    void serviceFileLossReleasesTheAttemptBeforeRecompilingWithNewFileIds() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong()))
                .thenReturn("source-one", "input-one", "source-two", "input-two");
        ObjectNode first = result("Accepted");
        first.putObject("fileIds").put("program.jar", "artifact-one");
        ObjectNode second = result("Accepted");
        second.putObject("fileIds").put("program.jar", "artifact-two");
        when(client.execute(any(), anyLong()))
                .thenReturn(List.of(first))
                .thenThrow(new GoJudgeFileMissingException())
                .thenReturn(List.of(second))
                .thenReturn(List.of(result("Accepted")));
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            assertThrows(GoJudgeFileMissingException.class, () -> executor.run(session, program, stream("first")));
        }
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            GoJudgeExecutor.PreparedProgram program = executor.compile(session);
            assertEquals("artifact-two", program.fileId());
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.run(session, program, stream("second")).status());
        }
        var order = inOrder(client);
        order.verify(client).delete(eq("source-one"), anyLong());
        order.verify(client).delete(eq("input-one"), anyLong());
        order.verify(client).delete(eq("artifact-one"), anyLong());
        order.verify(client).upload(any(InputStream.class), eq("Main.java"), anyLong());
        order.verify(client).delete(eq("source-two"), anyLong());
        order.verify(client).delete(eq("input-two"), anyLong());
        order.verify(client).delete(eq("artifact-two"), anyLong());
    }

    @Test
    void cancelledExecutionStillDeletesInputAndArtifactWithoutLosingTheInterrupt() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("source-id", "input-id");
        ObjectNode compiled = result("Accepted");
        compiled.putObject("fileIds").put("program.jar", "artifact-id");
        when(client.execute(any(), anyLong())).thenReturn(List.of(compiled)).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new GoJudgeException("cancelled");
        });
        doAnswer(invocation -> {
            assertFalse(Thread.currentThread().isInterrupted(), "租约中断不能阻止远端文件清理");
            return null;
        }).when(client).delete(anyString(), anyLong());
        try {
            try (GoJudgeTaskSession session = executor.openTask(task)) {
                GoJudgeExecutor.PreparedProgram program = executor.compile(session);
                assertThrows(GoJudgeException.class, () -> executor.run(session, program, stream("")));
            }
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        verify(client).delete(eq("source-id"), anyLong());
        verify(client).delete(eq("input-id"), anyLong());
        verify(client).delete(eq("artifact-id"), anyLong());
    }

    private ObjectNode result(String status) {
        ObjectNode result = mapper.createObjectNode();
        result.put("status", status);
        result.put("exitStatus", 0);
        result.put("time", 100L);
        result.put("runTime", 200L);
        result.put("memory", 1024L);
        result.putObject("files").put("stdout", "").put("stderr", "").put("launcher", "");
        return result;
    }

    private InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param client 隔离或真实沙箱客户端
     * @param properties 本测试使用的容量、截止或发布配置
     * @param worker 本测试使用的 Worker 资源配置
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static GoJudgeExecutor createGoJudgeExecutor(
            GoJudgeClient client,
            GoJudgeProperties properties,
            WorkerProperties worker) {
        GoJudgeExecutor component = new GoJudgeExecutor();
        ReflectionTestUtils.setField(component, "client", client);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "worker", worker);
        return component;
    }
}
