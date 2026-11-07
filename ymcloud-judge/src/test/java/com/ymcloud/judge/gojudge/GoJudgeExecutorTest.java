package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.pojo.contract.judge.JudgeLanguage;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import static org.mockito.Mockito.never;
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

    /**
     * 准备固定任务和独立文件流，所有沙箱调用均由测试控制
     * @throws Exception 模拟客户端配置失败
     */
    @BeforeEach
    void setup() throws Exception {
        client = mock(GoJudgeClient.class);
        when(client.openFile(anyString(), anyLong())).thenAnswer(invocation -> stream(""));
        worker = new WorkerProperties();
        executor = createGoJudgeExecutor(client, new GoJudgeProperties(), worker);
        JudgeLanguage language = new JudgeLanguage();
        language.setName("Java");
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

    /**
     * 编译及题目运行时限独立于文件传输预算，发送到沙箱的墙钟限制保持原值
     * @throws Exception 模拟沙箱调用失败
     */
    @Test
    void executionLimitsAreIndependentOfTransferTimeout() throws Exception {
        GoJudgeProperties properties = new GoJudgeProperties();
        properties.setToken("test-go-judge-token-0000000000000000000");
        properties.setRequestTimeout(Duration.ofSeconds(3));
        properties.validate();
        executor = createGoJudgeExecutor(client, properties, worker);
        task.setTimeLimit(60000L);
        assertNull(executor.capacityFailure(task));
        when(client.upload(any(InputStream.class), eq("stdin"), anyLong())).thenReturn("input-id");
        when(client.execute(any(), anyLong())).thenAnswer(invocation -> {
            Map<?, ?> request = invocation.getArgument(0);
            Map<?, ?> command = (Map<?, ?>) ((List<?>) request.get("cmd")).get(0);
            assertEquals(60_000_000_000L, command.get("clockLimit"));
            return List.of(result("Accepted"));
        });
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            var program = new GoJudgeExecutor.PreparedProgram("Java", "program.jar", "artifact-id");
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.run(session, program, stream("")).status());
        }
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

    /**
     * 成功执行必须保留原始输出引用，缺失文件不能退化为空答案
     * @throws Exception 响应解析失败
     */
    @Test
    void successfulExecutionRequiresRawOutputReference() throws Exception {
        ObjectNode response = result("Accepted");
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.classify(response, task).status());
        ((ObjectNode) response.get("fileIds")).remove("stdout");
        assertThrows(IOException.class, () -> executor.classify(response, task));
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

    /**
     * 四种语言映射到固定编译命令，产物与诊断文件均纳入会话清理
     * @throws Exception 模拟编译或产物清理失败
     */
    @Test
    void fourEntrypointsUseFixedCommandsAndCacheCompleteArtifacts() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("source-id");
        for (String language : List.of("Java", "C", "C++", "Python")) {
            task.getLanguageConfig().setName(language);
            String artifact = language.equals("Java") ? "program.jar" : language.equals("Python") ? "main.py" : "program";
            ObjectNode response = result("Accepted");
            ((ObjectNode) response.get("fileIds")).put(artifact, "artifact-id");
            doAnswer(invocation -> {
                Map<?, ?> request = invocation.getArgument(0);
                Map<?, ?> command = (Map<?, ?>) ((List<?>) request.get("cmd")).get(0);
                String arguments = command.get("args").toString();
                assertTrue(arguments.startsWith("[/usr/local/bin/ymcloud-output-limit, "
                        + worker.getMaxOutputBytes() + ", " + new GoJudgeProperties().getMaxFileBytes() + ","));
                assertEquals(512L * 1024 * 1024, command.get("memoryLimit"));
                assertEquals(10_000_000_000L, command.get("clockLimit"));
                assertEquals(false, command.get("copyOutTruncate"));
                assertEquals(List.of("stdout", "stderr", artifact + "?"), command.get("copyOutCached"));
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
            ((ObjectNode) response.get("fileIds")).put("program.jar", "partial-artifact");
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
            ((ObjectNode) response.get("fileIds")).put("program.jar", "artifact-id");
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
            assertEquals(List.of("stdout", "stderr"), command.get("copyOutCached"));
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

    /**
     * 普通判题仅下载短预览，完整输出留给流式比较且 stderr 立即回收
     * @throws Exception 模拟执行或文件读取失败
     */
    @Test
    void normalRunKeepsFullOutputCachedAndReturnsOnlyPreview() throws Exception {
        when(client.upload(any(InputStream.class), eq("stdin"), anyLong())).thenReturn("input-id");
        when(client.execute(any(), anyLong())).thenReturn(List.of(result("Accepted")));
        when(client.openFile(eq("stdout-id"), anyLong())).thenAnswer(invocation -> stream("x".repeat(20000)));
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            var result = executor.run(session,
                    new GoJudgeExecutor.PreparedProgram("Java", "program.jar", "artifact-id"), stream("input"));
            assertEquals("x".repeat(301), result.stdout());
            assertEquals("stdout-id", result.stdoutFileId());
            verify(client).delete(eq("stderr-id"), anyLong());
            verify(client, never()).openFile(eq("stderr-id"), anyLong());
        }
        verify(client).delete(eq("stdout-id"), anyLong());
    }

    /**
     * 大编译诊断同样缓存为文件，用户错误消息维持既有 4000 字符上限
     * @throws Exception 模拟编译或文件读取失败
     */
    @Test
    void compilerDiagnosticsKeepExistingCharacterLimitAndReleaseCachedStreams() throws Exception {
        when(client.upload(any(InputStream.class), anyString(), anyLong())).thenReturn("source-id");
        when(client.execute(any(), anyLong())).thenReturn(List.of(result("Nonzero Exit Status")));
        when(client.openFile(eq("stderr-id"), anyLong())).thenAnswer(invocation -> stream("E".repeat(20000)));
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            CompileException failure = assertThrows(CompileException.class, () -> executor.compile(session));
            assertEquals("编译失败（Nonzero Exit Status）：" + "E".repeat(4000), failure.getMessage());
            verify(client).delete(eq("stdout-id"), anyLong());
            verify(client).delete(eq("stderr-id"), anyLong());
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
        ((ObjectNode) first.get("fileIds")).put("program.jar", "artifact-one");
        ObjectNode second = result("Accepted");
        ((ObjectNode) second.get("fileIds")).put("program.jar", "artifact-two");
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
        ((ObjectNode) compiled.get("fileIds")).put("program.jar", "artifact-id");
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

    /**
     * SPJ 编译提供真实 testlib 头文件，编译结束立即回收源码依赖。
     * @throws Exception 沙箱模拟失败
     */
    @Test
    void spjCompilerSuppliesPinnedTestlibAndReleasesDependencies() throws Exception {
        when(client.upload(any(InputStream.class), eq("main.cpp"), anyLong())).thenReturn("source-id");
        when(client.upload(any(InputStream.class), eq("testlib.h"), anyLong())).thenAnswer(invocation -> {
            String header = new String(((InputStream) invocation.getArgument(0)).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(header.contains("void registerTestlibCmd(int argc, char *argv[])"));
            assertTrue(header.contains("#define VERSION \"0.9.49\""));
            return "header-id";
        });
        ObjectNode compiled = result("Accepted");
        ((ObjectNode) compiled.get("fileIds")).put("checker", "checker-id");
        when(client.execute(any(), anyLong())).thenAnswer(invocation -> {
            Map<?, ?> command = (Map<?, ?>) ((List<?>) ((Map<?, ?>) invocation.getArgument(0)).get("cmd")).get(0);
            assertEquals(Map.of("main.cpp", Map.of("fileId", "source-id"),
                    "testlib.h", Map.of("fileId", "header-id")), command.get("copyIn"));
            assertTrue(command.get("args").toString().contains("-std=c++17"));
            assertEquals(List.of("stdout", "stderr", "checker?"), command.get("copyOutCached"));
            return List.of(compiled);
        });
        SpjProgram spj = new SpjProgram();
        spj.setLanguage("C++");
        spj.setCode("#include \"testlib.h\"");
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            assertEquals("checker", executor.compileSpj(session, spj).artifact());
            verify(client).delete(eq("source-id"), anyLong());
            verify(client).delete(eq("header-id"), anyLong());
        }
        verify(client).delete(eq("checker-id"), anyLong());
    }

    /**
     * testlib 退出判定与校验器资源故障分开解释，不接受未知部分分协议。
     * @throws Exception 响应解析失败
     */
    @Test
    void testlibVerdictsAndCheckerFaultsRemainDistinct() throws Exception {
        var mapping = Map.of(0, JudgeVerdict.STATUS_ACCEPTED, 1, JudgeVerdict.STATUS_WRONG_ANSWER,
                2, JudgeVerdict.STATUS_PRESENTATION_ERROR, 3, JudgeVerdict.STATUS_SYSTEM_ERROR,
                7, JudgeVerdict.STATUS_SYSTEM_ERROR, 50, JudgeVerdict.STATUS_SYSTEM_ERROR);
        for (var entry : mapping.entrySet()) {
            ObjectNode response = result(entry.getKey() == 0 ? "Accepted" : "Nonzero Exit Status");
            response.put("exitStatus", entry.getKey());
            assertEquals(entry.getValue(), executor.checkerVerdict(response));
        }
        for (String state : List.of("Time Limit Exceeded", "Memory Limit Exceeded", "Signalled", "Output Limit Exceeded")) {
            assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, executor.checkerVerdict(result(state)));
        }
        ObjectNode response = result("Accepted");
        response.put("runTime", 2_000_000_001L);
        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, executor.checkerVerdict(response));
        response.put("runTime", 1L);
        response.put("memory", SpjProgram.MEMORY_LIMIT_BYTES + 1);
        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, executor.checkerVerdict(response));
    }

    /**
     * 选手只得到输入与程序，原始输出以文件 ID 传给独立 checker，隐藏答案不混入选手沙箱。
     * @throws Exception 模拟上传或执行失败
     */
    @Test
    void spjUsesRawOutputAndSeparateCheckerSandbox() throws Exception {
        task.setSpj(new SpjProgram());
        when(client.upload(any(InputStream.class), anyString(), anyLong()))
                .thenReturn("stdin-id", "test-input-id", "answer-id");
        ObjectNode ran = result("Accepted");
        ((ObjectNode) ran.get("files")).remove("stdout");
        ((ObjectNode) ran.get("fileIds")).put("stdout", "raw-output-id");
        when(client.execute(any(), anyLong())).thenAnswer(invocation -> {
            Map<?, ?> command = (Map<?, ?>) ((List<?>) ((Map<?, ?>) invocation.getArgument(0)).get("cmd")).get(0);
            if (((Map<?, ?>) command.get("copyIn")).containsKey("program.jar")) {
                assertEquals(List.of("stdout", "stderr"), command.get("copyOutCached"));
                assertEquals(Map.of("program.jar", Map.of("fileId", "program-id")), command.get("copyIn"));
                return List.of(ran);
            }
            assertEquals(Map.of("checker", Map.of("fileId", "checker-id"),
                    "input.txt", Map.of("fileId", "test-input-id"),
                    "output.txt", Map.of("fileId", "raw-output-id"),
                    "answer.txt", Map.of("fileId", "answer-id")), command.get("copyIn"));
            assertTrue(command.get("args").toString().endsWith("./checker, input.txt, output.txt, answer.txt]"));
            return List.of(result("Accepted"));
        });
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            var run = executor.run(session, new GoJudgeExecutor.PreparedProgram("Java", "program.jar", "program-id"), stream("input"));
            assertEquals("", run.stdout());
            assertEquals("raw-output-id", run.stdoutFileId());
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, executor.check(session,
                    new GoJudgeExecutor.PreparedProgram("C++", "checker", "checker-id"),
                    stream("input"), stream("hidden answer"), run.stdoutFileId()));
        }
        for (String id : List.of("stdin-id", "test-input-id", "answer-id", "raw-output-id")) {
            verify(client).delete(eq(id), anyLong());
        }
    }

    /**
     * 构造包含输出文件引用的固定沙箱响应
     * @param status 要模拟的沙箱状态
     * @return 带资源统计和受信诊断的响应
     */
    private ObjectNode result(String status) {
        ObjectNode result = mapper.createObjectNode();
        result.put("status", status);
        result.put("exitStatus", 0);
        result.put("time", 100L);
        result.put("runTime", 200L);
        result.put("memory", 1024L);
        result.putObject("files").put("launcher", "");
        result.putObject("fileIds").put("stdout", "stdout-id").put("stderr", "stderr-id");
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
}
