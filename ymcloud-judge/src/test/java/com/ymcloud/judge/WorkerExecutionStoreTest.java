package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.WorkerExecutionStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerExecutionStoreTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void lateHeartbeatCannotInterruptConsumerAfterItsLeaseWasClosed() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(11L);
        CountDownLatch renewing = new CountDownLatch(1);
        CountDownLatch renewalResponse = new CountDownLatch(1);
        CountDownLatch consumerCanFinish = new CountDownLatch(1);
        CountDownLatch claimed = new CountDownLatch(1);
        AtomicReference<WorkerExecutionStore.Lease> lease = new AtomicReference<>();
        doAnswer(invocation -> {
            renewing.countDown();
            assertTrue(renewalResponse.await(5, TimeUnit.SECONDS));
            return 0L;
        }).when(redis).execute(any(DefaultRedisScript.class), anyList(), any(), any());

        WorkerProperties properties = new WorkerProperties();
        properties.setLeaseMillis(3000L);
        WorkerExecutionStore store = new WorkerExecutionStore(redis,
                new WorkerAmqpConfig().workerObjectMapper(), properties);
        var consumer = Executors.newSingleThreadExecutor();
        try {
            var nextTask = consumer.submit(() -> {
                lease.set(store.claim(WorkerFixtures.task(1, 0)).lease());
                claimed.countDown();
                consumerCanFinish.await();
                return Thread.currentThread().isInterrupted();
            });
            assertTrue(claimed.await(5, TimeUnit.SECONDS));
            assertTrue(renewing.await(5, TimeUnit.SECONDS));
            lease.get().close();
            renewalResponse.countDown();
            ScheduledExecutorService heartbeat = (ScheduledExecutorService)
                    ReflectionTestUtils.getField(store, "heartbeat");
            assertNotNull(heartbeat);
            // 单线程心跳池上的屏障保证已开始的续租回调完全退出。
            heartbeat.submit(() -> { }).get(5, TimeUnit.SECONDS);
            consumerCanFinish.countDown();
            assertFalse(nextTask.get(5, TimeUnit.SECONDS));
        } finally {
            renewalResponse.countDown();
            consumerCanFinish.countDown();
            consumer.shutdownNow();
            store.stop();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void leaseReleaseFailureDoesNotFailAlreadyAcknowledgedTask() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(11L);
        doThrow(new IllegalStateException("Redis disconnected during release"))
                .when(redis).execute(any(DefaultRedisScript.class), anyList(), any());
        WorkerExecutionStore store = new WorkerExecutionStore(redis,
                new WorkerAmqpConfig().workerObjectMapper(), new WorkerProperties());
        try {
            WorkerExecutionStore.Claim claim = store.claim(WorkerFixtures.task(1, 0));
            assertEquals(1, claim.lease().attempt());
            assertDoesNotThrow(claim.lease()::close);
        } finally {
            store.stop();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void missingOrInvalidAcquireStatusCannotCreateLeaseWithInvalidAttempt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(null, 1L, Long.MAX_VALUE);
        WorkerExecutionStore store = new WorkerExecutionStore(redis,
                new WorkerAmqpConfig().workerObjectMapper(), new WorkerProperties());
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThrows(IllegalStateException.class, () -> store.claim(WorkerFixtures.task(1, 0)));
            }
        } finally {
            store.stop();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void corruptedAndNullCompletedJsonReachRecoveryValidationRatherThanAcquireExecutionLease() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(2L);
        WorkerExecutionStore store = new WorkerExecutionStore(redis,
                new WorkerAmqpConfig().workerObjectMapper(), new WorkerProperties());
        try {
            for (String corrupted : new String[]{"{corrupt}", "null", "x".repeat(16 * 1024 * 1024 + 1)}) {
                when(hashes.get(any(), any())).thenReturn(corrupted);
                WorkerExecutionStore.Claim claim = store.claim(WorkerFixtures.task(1, 0));
                assertNotNull(claim.completed());
                assertFalse(claim.completed().isTerminal());
                assertFalse(claim.busy());
            }
        } finally {
            store.stop();
        }
    }
}
