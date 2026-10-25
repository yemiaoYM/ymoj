package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.gojudge.GoJudgeCapacityException;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 验证固定版本租约、逐点流式读取、原有评分及 go-judge 的资源和故障边界。
 */
@ExtendWith(MockitoExtension.class)
class JudgeRunVersionedDataTest {

    @Mock
    GoJudgeExecutor executor;
    @Mock
    TestDataCache cache;
    @Mock
    GoJudgeTaskSession session;

    final GoJudgeExecutor.PreparedProgram program = new GoJudgeExecutor.PreparedProgram("Java", "program.jar", "artifact");
    JudgeRun runner;
    List<JudgeResult> progress;

    @BeforeEach
    void setup() {
        runner = createJudgeRun(executor, cache, new WorkerProperties());
        progress = new ArrayList<>();
    }

    @Test
    void formalTaskUsesPinnedVersionStreamsEachPointAndKeepsMaximumStatistics() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        prepare(task);
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
        List<String> inputs = new ArrayList<>();
        when(executor.run(eq(session), eq(program), any(InputStream.class)))
                .thenAnswer(invocation -> {
                    InputStream input = invocation.getArgument(2);
                    inputs.add(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    return accepted("answer\n", inputs.size() == 1 ? 12L : 3L, inputs.size() == 1 ? 20L : 30L);
                });
        JudgeResult result = runner.execute(task, progress::add);
        assertEquals(List.of("  first\r\n\n", "second"), inputs);
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, result.getStatus());
        assertEquals(100, result.getScore());
        assertEquals(12L, result.getTime());
        assertEquals(30L, result.getMemory());
        var order = inOrder(first, second, executor, lease, session);
        order.verify(first).openInput();
        order.verify(executor).run(eq(session), eq(program), any(InputStream.class));
        order.verify(first).openOutput();
        order.verify(second).openInput();
        order.verify(executor).run(eq(session), eq(program), any(InputStream.class));
        order.verify(second).openOutput();
        order.verify(session).close();
        order.verify(lease).close();
        assertTrue(result.isTerminal());
        assertEquals(task.getExecutionId(), result.getExecutionId());
        assertEquals(List.of(1, 2), progress.stream().map(JudgeResult::getSequence).toList());
    }

    @Test
    void selfTestPreservesUnicodeWhitespaceAndEmptyInputWithoutDataCache() throws Exception {
        for (String value : List.of("", " \t中文😀\r\ntrailing  \n\n")) {
            JudgeTask task = WorkerFixtures.task(1, 0);
            task.setInput(value);
            prepare(task);
            doAnswer(invocation -> {
                InputStream input = invocation.getArgument(2);
                assertArrayEquals(value.getBytes(StandardCharsets.UTF_8), input.readAllBytes());
                return accepted("answer\n", 5L, 30L);
            }).when(executor).run(eq(session), eq(program), any(InputStream.class));
            assertEquals(JudgeVerdict.STATUS_ACCEPTED, runner.execute(task, progress::add).getStatus());
        }
        verifyNoInteractions(cache);
    }

    @Test
    void normalExitStillRequiresOutputComparison() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        prepare(task);
        when(executor.run(eq(session), eq(program), any(InputStream.class))).thenReturn(accepted("wrong", 2L, 20L));
        assertEquals(JudgeVerdict.STATUS_WRONG_ANSWER, runner.execute(task, progress::add).getStatus());
    }

    @Test
    void resourceFailureKeepsStructuredStatisticsAndNeverComparesOutput() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        prepare(task);
        when(executor.run(eq(session), eq(program), any(InputStream.class)))
                .thenReturn(new GoJudgeExecutor.ExecutionResult(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED,
                        "answer", null, 5L, task.getMemoryLimit() + 1));
        JudgeResult result = runner.execute(task, progress::add);
        assertEquals(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED, result.getStatus());
        assertEquals(task.getMemoryLimit() + 1, result.getMemory());
        assertEquals(0, result.getScore());
    }

    @Test
    void compilationIsCeButInfrastructureFailurePropagatesForBoundedRetry() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        when(executor.openTask(task)).thenReturn(session);
        when(executor.compile(session)).thenThrow(new CompileException("syntax"), new IOException("service restarted"));
        assertEquals(JudgeVerdict.STATUS_COMPILE_ERROR, runner.execute(task, progress::add).getStatus());
        assertThrows(IOException.class, () -> runner.execute(task, progress::add));
        verify(session, times(2)).close();
    }

    @Test
    void capacityFailureProducesTerminalSeBeforePreparingDataOrRunningCompiler() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        when(executor.capacityFailure(task)).thenReturn("题目内存限制超过当前评测部署容量");
        JudgeResult result = runner.execute(task, progress::add);
        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, result.getStatus());
        assertTrue(result.isTerminal());
        assertTrue(progress.isEmpty());
        verifyNoInteractions(cache, session);
        verify(executor).capacityFailure(task);
    }

    @Test
    void oversizedInputProducesExplicitTerminalSeWithoutRetrying() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        prepare(task);
        when(executor.run(eq(session), eq(program), any(InputStream.class)))
                .thenThrow(new GoJudgeCapacityException("输入文件超过当前部署的单文件容量"));
        JudgeResult result = runner.execute(task, progress::add);
        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, result.getStatus());
        assertTrue(result.isTerminal());
        assertEquals("输入文件超过当前部署的单文件容量", result.getErrorMessage());
        verify(session).close();
    }

    @Test
    void cacheFailureAndDeadlinePreventSandboxStartup() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        when(cache.acquire(anyLong(), anyLong(), anyString())).thenThrow(new IOException("hash mismatch"));
        assertThrows(IOException.class, () -> runner.execute(task, progress::add));
        task.setDeadlineEpochMs(System.currentTimeMillis() - 1);
        assertThrows(IOException.class, () -> runner.execute(task, progress::add));
        verifyNoInteractions(session);
    }

    private void prepare(JudgeTask task) throws IOException {
        when(executor.openTask(task)).thenReturn(session);
        when(executor.compile(session)).thenReturn(program);
    }

    private GoJudgeExecutor.ExecutionResult accepted(String output, long time, long memory) {
        return new GoJudgeExecutor.ExecutionResult(JudgeVerdict.STATUS_ACCEPTED, output, null, time, memory);
    }

    private InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param executor 受控沙箱执行边界
     * @param cache 本测试的版本或状态缓存
     * @param properties 本测试使用的容量、截止或发布配置
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeRun createJudgeRun(GoJudgeExecutor executor, TestDataCache cache, WorkerProperties properties) {
        JudgeRun component = new JudgeRun();
        ReflectionTestUtils.setField(component, "executor", executor);
        ReflectionTestUtils.setField(component, "cache", cache);
        ReflectionTestUtils.setField(component, "properties", properties);
        return component;
    }
}
