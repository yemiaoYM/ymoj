package com.ymcloud.judge;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.judge.bo.CodeRunResult;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.exception.SandBoxException;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 不启动 Docker，验证固定版本租约、逐点流式读取、评分、资源单位和错误边界。
 */
@ExtendWith(MockitoExtension.class)
class JudgeRunVersionedDataTest {

    @Mock
    DockerClient docker;
    @Mock
    TestDataCache cache;
    @Mock
    CodeExecContainer container;
    @Mock
    SandBox sandbox;
    @TempDir
    Path directory;
    JudgeRun runner;
    List<JudgeResult> progress;

    @BeforeEach
    void setup() {
        WorkerProperties properties = new WorkerProperties();
        properties.setWorkDir(directory);
        runner = new JudgeRun(docker, cache, properties);
        progress = new ArrayList<>();
    }

    @Test
    void formalTaskUsesPinnedVersionAndStreamsOnlyThePointBeingExecuted() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        TestDataCache.Lease lease = mock(TestDataCache.Lease.class);
        TestDataCache.CachedCase first = mock(TestDataCache.CachedCase.class);
        TestDataCache.CachedCase second = mock(TestDataCache.CachedCase.class);
        when(cache.acquire(task.getProblemId(), task.getTestDataVersionId(), task.getManifestSha256())).thenReturn(lease);
        when(lease.entries()).thenReturn(List.of(first, second));
        when(first.id()).thenReturn(1);
        when(second.id()).thenReturn(2);
        when(first.openInput()).thenReturn(stream("  first\r\n\n"));
        when(first.openOutput()).thenReturn(stream("answer  \r\n\n"));
        when(second.openInput()).thenReturn(stream("second"));
        when(second.openOutput()).thenReturn(stream("answer"));
        when(container.isRunning()).thenReturn(true);
        List<String> inputs = new ArrayList<>();
        when(sandbox.execute(eq(container), any(InputStream.class), eq(1200L), anyLong()))
                .thenAnswer(invocation -> {
                    InputStream input = invocation.getArgument(1);
                    inputs.add(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    return new CodeRunResult("answer\n", null, 3L, 20L);
                });
        JudgeResult result = run(task);
        assertEquals(List.of("  first\r\n\n", "second"), inputs);
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.getStatus());
        assertEquals(100, result.getScore());
        var order = inOrder(first, second, sandbox, lease);
        order.verify(first).openInput();
        order.verify(sandbox).execute(eq(container), any(InputStream.class), eq(1200L), anyLong());
        order.verify(first).openOutput();
        order.verify(second).openInput();
        order.verify(sandbox).execute(eq(container), any(InputStream.class), eq(1200L), anyLong());
        order.verify(second).openOutput();
        order.verify(lease).close();
        assertTrue(result.isTerminal());
        assertEquals(task.getExecutionId(), result.getExecutionId());
        assertEquals(List.of(1, 2), progress.stream().map(JudgeResult::getSequence).toList());
        verify(container).remove(true);
    }

    @Test
    void selfTestKeepsUnicodeAndWhitespaceAndNeverUsesCache() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        task.setInput(" \t中文😀\r\ntrailing  \n\n");
        when(container.isRunning()).thenReturn(true);
        when(sandbox.execute(eq(container), any(InputStream.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    InputStream input = invocation.getArgument(1);
                    assertArrayEquals(task.getInput().getBytes(StandardCharsets.UTF_8), input.readAllBytes());
                    return new CodeRunResult("answer\n", null, 5L, 30L);
                });
        JudgeResult result = run(task);
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.getStatus());
        verifyNoInteractions(cache);
    }

    @Test
    void memoryIsComparedInKiBAndOiRuleRemainsCompatible() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        when(container.isRunning()).thenReturn(true);
        when(sandbox.execute(eq(container), any(InputStream.class), anyLong(), anyLong()))
                .thenReturn(new CodeRunResult("answer", null, 5L, task.getMemoryLimit() + 1));
        JudgeResult result = run(task);
        assertEquals(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED, result.getStatus());
        assertEquals(0, result.getScore());
        assertEquals(task.getMemoryLimit() + 1, result.getMemory());
    }

    @Test
    void compileFailureIsCeButSandboxFailurePropagatesForBoundedRetry() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        doThrow(new CompileException("syntax")).when(sandbox).compile(eq(container), anyLong());
        assertEquals(JudgeVerdict.STATUS_COMPILE_ERROR, run(task).getStatus());
        reset(sandbox);
        doThrow(new SandBoxException("Docker unavailable")).when(sandbox).compile(eq(container), anyLong());
        assertThrows(SandBoxException.class, () -> run(task));
    }

    @Test
    void cacheFailureOrExpiredDeadlinePreventsStartingSandbox() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        when(cache.acquire(anyLong(), anyLong(), anyString())).thenThrow(new java.io.IOException("hash mismatch"));
        assertThrows(java.io.IOException.class, () -> runner.execute(task, progress::add));
        task.setDeadlineEpochMs(System.currentTimeMillis() - 1);
        assertThrows(SandBoxException.class, () -> runner.execute(task, progress::add));
        verifyNoInteractions(docker, container, sandbox);
    }

    @Test
    void newlyCreatedContainerIsRemovedWhenStartupFails() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        doThrow(new SandBoxException("Docker start unavailable")).when(container).start();
        try (MockedConstruction<CodeExecContainerFactory> factories = mockConstruction(
                CodeExecContainerFactory.class,
                (factory, context) -> when(factory.createDockerContainer()).thenReturn(container))) {
            assertThrows(SandBoxException.class, () -> runner.execute(task, progress::add));
        }
        verify(container).remove(true);
        try (var workDirectories = Files.list(directory)) {
            assertEquals(0, workDirectories.count());
        }
        verifyNoInteractions(sandbox, cache);
    }

    private JudgeResult run(JudgeTask task) throws Exception {
        try (MockedConstruction<CodeExecContainerFactory> factories = mockConstruction(CodeExecContainerFactory.class,
                (factory, context) -> when(factory.createDockerContainer()).thenReturn(container));
             MockedConstruction<SandBoxFactory> sandboxes = mockConstruction(SandBoxFactory.class,
                     (factory, context) -> when(factory.createSandBox(container)).thenReturn(sandbox))) {
            return runner.execute(task, progress::add);
        }
    }

    private InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
