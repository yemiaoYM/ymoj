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
import com.ymcloud.pojo.contract.judge.SpjProgram;
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
import static org.mockito.Mockito.never;
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
        when(session.openFile("raw-output")).thenAnswer(invocation -> stream("answer\n"));
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
        var order = inOrder(first, second, executor, cache, lease, session);
        order.verify(executor).openTask(task);
        order.verify(cache).acquire(task.getProblemId(), task.getTestDataVersionId(), task.getManifestSha256());
        order.verify(first).openInput();
        order.verify(executor).run(eq(session), eq(program), any(InputStream.class));
        order.verify(first).openOutput();
        order.verify(second).openInput();
        order.verify(executor).run(eq(session), eq(program), any(InputStream.class));
        order.verify(second).openOutput();
        order.verify(lease).close();
        order.verify(session).close();
        assertTrue(result.isTerminal());
        assertEquals(task.getExecutionId(), result.getExecutionId());
        assertEquals(List.of(1, 2), progress.stream().map(JudgeResult::getSequence).toList());
    }

    @Test
    void selfTestPreservesUnicodeWhitespaceAndEmptyInputWithoutDataCache() throws Exception {
        when(session.openFile("raw-output")).thenAnswer(invocation -> stream("answer\n"));
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
        when(session.openFile("raw-output")).thenAnswer(invocation -> stream("wrong"));
        when(executor.run(eq(session), eq(program), any(InputStream.class))).thenReturn(accepted("wrong", 2L, 20L));
        assertEquals(JudgeVerdict.STATUS_WRONG_ANSWER, runner.execute(task, progress::add).getStatus());
    }

    /**
     * 普通判题下载原始输出失败时释放文件并传播故障，不将不完整正文判为 WA
     * @throws Exception 模拟沙箱或网络边界失败
     */
    @Test
    void normalOutputDownloadFailureReleasesFileAndPropagates() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        prepare(task);
        when(executor.run(eq(session), eq(program), any(InputStream.class))).thenReturn(accepted("answer", 2L, 20L));
        when(session.openFile("raw-output")).thenThrow(new IOException("download unavailable"));
        assertThrows(IOException.class, () -> runner.execute(task, progress::add));
        verify(session).release("raw-output");
        verify(session).close();
    }

    @Test
    void resourceFailureKeepsStructuredStatisticsAndNeverComparesOutput() throws Exception {
        JudgeTask task = WorkerFixtures.task(1, 0);
        prepare(task);
        when(executor.run(eq(session), eq(program), any(InputStream.class)))
                .thenReturn(new GoJudgeExecutor.ExecutionResult(JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED,
                        "answer", null, 5L, task.getMemoryLimit() + 1, null));
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

    /**
     * 数据准备失败也释放先取得的任务额度，截止任务则不再申请会话
     * @throws Exception 模拟数据下载失败
     */
    @Test
    void cacheFailureReleasesTaskSlotAndExpiredTaskDoesNotAcquireIt() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        when(executor.openTask(task)).thenReturn(session);
        when(cache.acquire(anyLong(), anyLong(), anyString())).thenThrow(new IOException("hash mismatch"));
        assertThrows(IOException.class, () -> runner.execute(task, progress::add));
        task.setDeadlineEpochMs(System.currentTimeMillis() - 1);
        assertThrows(IOException.class, () -> runner.execute(task, progress::add));
        verify(executor).openTask(task);
        verify(session).close();
        verify(executor, never()).compile(any());
    }

    /**
     * 多测试点共用一个 checker，AC 不再经过字符串比较，checker 故障不能折算为 OI 部分分。
     * @throws Exception 模拟执行失败
     */
    @Test
    void spjReusesCheckerAndKeepsInfrastructureFailureOutOfOiScore() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 1);
        task.setSpj(new SpjProgram());
        prepare(task);
        var checker = new GoJudgeExecutor.PreparedProgram("C++", "checker", "checker-artifact");
        when(executor.compileSpj(session, task.getSpj())).thenReturn(checker);
        TestDataCache.Lease lease = mock(TestDataCache.Lease.class);
        TestDataCache.CachedCase first = mock(TestDataCache.CachedCase.class);
        TestDataCache.CachedCase second = mock(TestDataCache.CachedCase.class);
        when(cache.acquire(anyLong(), anyLong(), anyString())).thenReturn(lease);
        when(lease.entries()).thenReturn(List.of(first, second));
        when(first.openInput()).thenAnswer(invocation -> stream("input1"));
        when(second.openInput()).thenAnswer(invocation -> stream("input2"));
        when(first.openOutput()).thenAnswer(invocation -> stream("different answer1"));
        when(second.openOutput()).thenAnswer(invocation -> stream("different answer2"));
        when(executor.run(eq(session), eq(program), any(InputStream.class)))
                .thenReturn(new GoJudgeExecutor.ExecutionResult(JudgeVerdict.STATUS_ACCEPTED, "", null, 5, 20, "raw1"),
                        new GoJudgeExecutor.ExecutionResult(JudgeVerdict.STATUS_ACCEPTED, "", null, 8, 15, "raw2"));
        when(executor.check(eq(session), eq(checker), any(InputStream.class), any(InputStream.class), anyString()))
                .thenReturn(JudgeVerdict.STATUS_ACCEPTED);
        JudgeResult accepted = runner.execute(task, progress::add);
        assertEquals(JudgeVerdict.STATUS_ACCEPTED, accepted.getStatus());
        assertEquals(100, accepted.getScore());
        assertEquals(8, accepted.getTime());
        assertEquals(20, accepted.getMemory());
        verify(executor).compileSpj(session, task.getSpj());
        verify(session).release("raw1");
        verify(session).release("raw2");

        when(executor.check(eq(session), eq(checker), any(InputStream.class), any(InputStream.class), anyString()))
                .thenReturn(JudgeVerdict.STATUS_ACCEPTED, JudgeVerdict.STATUS_SYSTEM_ERROR);
        JudgeResult failed = runner.execute(task, progress::add);
        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, failed.getStatus());
        assertEquals(0, failed.getScore());
        assertTrue(failed.isTerminal());
        verify(session, times(2)).close();
        verify(lease, times(2)).close();
    }

    /**
     * 校验器编译失败属于题目配置错误，不能以选手编译错误返回私有源码诊断
     * @throws Exception 模拟数据准备或编译失败
     */
    @Test
    void spjCompileFailureKeepsCheckerDiagnosticsOutOfContestantResult() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        task.setSpj(new SpjProgram());
        prepare(task);
        TestDataCache.Lease lease = mock(TestDataCache.Lease.class);
        TestDataCache.CachedCase testCase = mock(TestDataCache.CachedCase.class);
        when(cache.acquire(anyLong(), anyLong(), anyString())).thenReturn(lease);
        when(lease.entries()).thenReturn(List.of(testCase));
        when(executor.compileSpj(session, task.getSpj()))
                .thenThrow(new CompileException("private checker source and answer"));

        JudgeResult result = runner.execute(task, progress::add);

        assertEquals(JudgeVerdict.STATUS_SYSTEM_ERROR, result.getStatus());
        assertEquals(0, result.getScore());
        assertEquals("SPJ 编译失败，请联系题目管理员", result.getErrorMessage());
        assertTrue(result.isTerminal());
        verify(executor, never()).run(any(), any(), any());
        verify(session).close();
        verify(lease).close();
    }

    /**
     * 校验器传输故障继续交由任务重试，但当前测试点的原始输出及版本租约必须释放
     * @throws Exception 模拟数据读取或校验器执行失败
     */
    @Test
    void spjTransportFailureReleasesRawOutputAndVersionLease() throws Exception {
        JudgeTask task = WorkerFixtures.task(0, 0);
        task.setSpj(new SpjProgram());
        prepare(task);
        var checker = new GoJudgeExecutor.PreparedProgram("C++", "checker", "checker-artifact");
        when(executor.compileSpj(session, task.getSpj())).thenReturn(checker);
        TestDataCache.Lease lease = mock(TestDataCache.Lease.class);
        TestDataCache.CachedCase testCase = mock(TestDataCache.CachedCase.class);
        when(cache.acquire(anyLong(), anyLong(), anyString())).thenReturn(lease);
        when(lease.entries()).thenReturn(List.of(testCase));
        when(testCase.openInput()).thenAnswer(invocation -> stream("input"));
        when(testCase.openOutput()).thenAnswer(invocation -> stream("hidden answer"));
        when(executor.run(eq(session), eq(program), any(InputStream.class)))
                .thenReturn(new GoJudgeExecutor.ExecutionResult(
                        JudgeVerdict.STATUS_ACCEPTED, "", null, 5, 20, "raw-output"));
        when(executor.check(eq(session), eq(checker), any(InputStream.class), any(InputStream.class), eq("raw-output")))
                .thenThrow(new IOException("sandbox unavailable"));

        assertThrows(IOException.class, () -> runner.execute(task, progress::add));

        var order = inOrder(session, lease);
        order.verify(session).release("raw-output");
        order.verify(lease).close();
        order.verify(session).close();
    }

    private void prepare(JudgeTask task) throws IOException {
        when(executor.openTask(task)).thenReturn(session);
        when(executor.compile(session)).thenReturn(program);
    }

    private GoJudgeExecutor.ExecutionResult accepted(String output, long time, long memory) {
        return new GoJudgeExecutor.ExecutionResult(JudgeVerdict.STATUS_ACCEPTED, output, null, time, memory, "raw-output");
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
