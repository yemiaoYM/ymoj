package com.ymcloud.judge.gojudge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.listeners.SpjCompileListener;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.SpjCompileRequest;
import com.ymcloud.pojo.contract.judge.SpjCompileResult;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-07
 * @Description: 验证正式任务与编译预检共用任务额度，以及等待和清理异常的释放边界
 */
class GoJudgeTaskCapacityTest {

    private final ObjectMapper mapper = new WorkerAmqpConfig().workerObjectMapper();
    private GoJudgeClient client;
    private GoJudgeExecutor executor;

    /**
     * 使用真实的单槽执行器，只模拟外部沙箱
     */
    @BeforeEach
    void setup() {
        client = mock(GoJudgeClient.class);
        WorkerProperties worker = new WorkerProperties();
        worker.setConcurrency(1);
        executor = spy(new GoJudgeExecutor());
        ReflectionTestUtils.setField(executor, "client", client);
        ReflectionTestUtils.setField(executor, "properties", new GoJudgeProperties());
        ReflectionTestUtils.setField(executor, "worker", worker);
        executor.initialize();
    }

    /**
     * 正式任务占满额度时编译预检必须等待，预检执行中后续正式任务也不能绕过上限
     * @throws Exception 线程或消息序列化失败
     */
    @Test
    void formalTaskAndSpjPrecheckShareOneLimitWithoutNestedAcquisition() throws Exception {
        CountDownLatch compiling = new CountDownLatch(1);
        CountDownLatch finishCompilation = new CountDownLatch(1);
        doAnswer(invocation -> {
            compiling.countDown();
            assertTrue(finishCompilation.await(5, TimeUnit.SECONDS));
            return new GoJudgeExecutor.PreparedProgram("C++", "checker", "checker-id");
        }).when(executor).compileSpj(any(), any());
        SpjCompileListener listener = listener();
        Message request = request();
        var thread = Executors.newSingleThreadExecutor();
        try (GoJudgeTaskSession formal = executor.openTask(task(10000))) {
            var reply = thread.submit(() -> listener.compile(request));
            assertFalse(compiling.await(100, TimeUnit.MILLISECONDS));
            formal.close();
            assertTrue(compiling.await(5, TimeUnit.SECONDS));
            assertThrows(IOException.class, () -> executor.openTask(task(30)));
            finishCompilation.countDown();
            SpjCompileResult result = mapper.readValue(reply.get(5, TimeUnit.SECONDS).getBody(), SpjCompileResult.class);
            assertTrue(result.success());
            try (GoJudgeTaskSession next = executor.openTask(task(1000))) {
                assertNotNull(next);
            }
        } finally {
            finishCompilation.countDown();
            thread.shutdownNow();
            assertTrue(thread.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * 已过期和等待超时的任务不消耗或多归还额度，重复关闭也不能扩大上限
     * @throws Exception 任务会话创建失败
     */
    @Test
    void timeoutAndRepeatedCloseCannotCreateExtraCapacity() throws Exception {
        assertThrows(IOException.class, () -> executor.openTask(task(-1)));
        try (GoJudgeTaskSession first = executor.openTask(task(1000))) {
            assertThrows(IOException.class, () -> executor.openTask(task(30)));
            first.close();
            first.close();
            try (GoJudgeTaskSession second = executor.openTask(task(1000))) {
                assertThrows(IOException.class, () -> executor.openTask(task(30)));
            }
        }
        try (GoJudgeTaskSession next = executor.openTask(task(1000))) {
            assertNotNull(next);
        }
    }

    /**
     * 被中断的等待立即退出并保留中断标记，不释放其他任务持有的额度
     * @throws Exception 等待线程未按时退出
     */
    @Test
    void interruptedWaitPreservesInterruptAndDoesNotReleaseAnotherTaskSlot() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try (GoJudgeTaskSession unexpected = executor.openTask(task(10000))) {
                failure.set(new AssertionError("等待任务不应取得被占用的额度"));
            } catch (Throwable error) {
                failure.set(error);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try (GoJudgeTaskSession formal = executor.openTask(task(10000))) {
            waiter.start();
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            waiter.interrupt();
            waiter.join(5000);
            assertFalse(waiter.isAlive());
            assertTrue(failure.get() instanceof InterruptedIOException);
            assertTrue(interrupted.get());
            assertThrows(IOException.class, () -> executor.openTask(task(30)));
        } finally {
            waiter.interrupt();
            waiter.join(5000);
        }
        try (GoJudgeTaskSession next = executor.openTask(task(1000))) {
            assertNotNull(next);
        }
    }

    /**
     * 编译诊断失败返回原语义，同时释放真实会话持有的共享额度
     * @throws Exception 消息编解码或会话创建失败
     */
    @Test
    void spjCompilationFailureReleasesSharedSlot() throws Exception {
        doThrow(new CompileException("syntax error")).when(executor).compileSpj(any(), any());
        SpjCompileResult result = mapper.readValue(listener().compile(request()).getBody(), SpjCompileResult.class);
        assertFalse(result.success());
        assertEquals("syntax error", result.message());
        try (GoJudgeTaskSession next = executor.openTask(task(1000))) {
            assertNotNull(next);
        }
    }

    /**
     * 即使文件清理出现未预期异常，会话仍必须归还额度且不能重复归还
     * @throws Exception 文件登记或模拟清理失败
     */
    @Test
    void cleanupFailureStillReleasesSlotExactlyOnce() throws Exception {
        GoJudgeTaskSession first = executor.openTask(task(1000));
        first.track(mapper.readTree("{\"fileIds\":{\"stdout\":\"owned-file\"}}"));
        doThrow(new IllegalStateException("cleanup failure")).when(client).delete(anyString(), anyLong());
        assertThrows(IllegalStateException.class, first::close);
        first.close();
        try (GoJudgeTaskSession next = executor.openTask(task(1000))) {
            assertThrows(IOException.class, () -> executor.openTask(task(30)));
        }
    }

    /**
     * 装配使用真实共享执行器的独立编译入口
     * @return 已注入消息解析器和共享执行器的预检消费者
     */
    private SpjCompileListener listener() {
        SpjCompileListener listener = new SpjCompileListener();
        ReflectionTestUtils.setField(listener, "mapper", mapper);
        ReflectionTestUtils.setField(listener, "executor", executor);
        return listener;
    }

    /**
     * 创建校验通过的独立编译预检消息
     * @return 带请求身份、截止和源码摘要的消息
     * @throws IOException 消息序列化失败
     */
    private Message request() throws IOException {
        SpjProgram program = new SpjProgram();
        program.setLanguage("C++");
        program.setCode("#include \"testlib.h\"");
        program.setDigest(program.sourceDigest());
        return new Message(mapper.writeValueAsBytes(new SpjCompileRequest(UUID.randomUUID().toString(),
                System.currentTimeMillis() + 30000, program)));
    }

    /**
     * 创建带实际剩余等待时间的任务快照
     * @param remainingMillis 距截止的毫秒数，负数表示已过期
     * @return 仅用于会话准入的任务
     */
    private JudgeTask task(long remainingMillis) {
        return JudgeTask.builder()
                .executionId(UUID.randomUUID().toString())
                .deadlineEpochMs(System.currentTimeMillis() + remainingMillis)
                .build();
    }
}
