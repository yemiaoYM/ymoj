package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.WorkerTaskValidator;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-07
 * @Description: 验证独立消费入口保留并发和确认语义，停机等待覆盖合法任务与终态收尾
 */
class WorkerAmqpConfigTest {

    /**
     * 两个真实容器均按当前确认配置等待在途任务，不使用 Rabbit 默认五秒停机边界
     */
    @Test
    void bothContainersWaitForTaskDeadlineAndRetainIndependentConsumption() {
        WorkerAmqpConfig config = new WorkerAmqpConfig();
        ConnectionFactory connection = mock(ConnectionFactory.class);
        for (long confirmMillis : new long[]{10000L, 60000L}) {
            WorkerProperties properties = new WorkerProperties();
            properties.setConcurrency(3);
            properties.setConfirmTimeoutMillis(confirmMillis);
            properties.setLeaseMillis(confirmMillis * 3);
            properties.validate();
            var formal = config.workerListenerFactory(connection, properties).createListenerContainer(null);
            var precheck = config.spjListenerFactory(connection, properties).createListenerContainer(null);
            try {
                long expected = confirmMillis == 10000L ? 3682000L : 3782000L;
                assertEquals(expected, ReflectionTestUtils.<Long>invokeMethod(formal, "getShutdownTimeout"));
                assertEquals(expected, ReflectionTestUtils.<Long>invokeMethod(precheck, "getShutdownTimeout"));
                assertEquals(1, ReflectionTestUtils.<Integer>invokeMethod(formal, "getPrefetchCount"));
                assertEquals(1, ReflectionTestUtils.<Integer>invokeMethod(precheck, "getPrefetchCount"));
                assertEquals(3, ReflectionTestUtils.getField(formal, "concurrentConsumers"));
                assertEquals(3, ReflectionTestUtils.getField(formal, "maxConcurrentConsumers"));
                assertEquals(1, ReflectionTestUtils.getField(precheck, "concurrentConsumers"));
                assertEquals(1, ReflectionTestUtils.getField(precheck, "maxConcurrentConsumers"));
                assertEquals(AcknowledgeMode.MANUAL, formal.getAcknowledgeMode());
                assertEquals(AcknowledgeMode.AUTO, precheck.getAcknowledgeMode());
            } finally {
                formal.destroy();
                precheck.destroy();
            }
        }
    }

    /**
     * 提取协议常量不缩短原有一小时任务窗口，也不扩大提交时间容差
     */
    @Test
    void taskDeadlineValidationKeepsItsExistingBoundaries() {
        WorkerTaskValidator validator = new WorkerTaskValidator();
        var task = WorkerFixtures.task(0, 0);
        task.setSubmittedAtEpochMs(System.currentTimeMillis() + JudgeProtocol.MAX_CLOCK_SKEW_MILLIS - 1000L);
        task.setDeadlineEpochMs(task.getSubmittedAtEpochMs() + JudgeProtocol.MAX_TASK_DURATION_MILLIS);
        assertDoesNotThrow(() -> validator.validate(task));
        task.setDeadlineEpochMs(task.getDeadlineEpochMs() + 1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task));
        task.setSubmittedAtEpochMs(System.currentTimeMillis() + JudgeProtocol.MAX_CLOCK_SKEW_MILLIS + 5000L);
        task.setDeadlineEpochMs(task.getSubmittedAtEpochMs() + JudgeProtocol.MAX_TASK_DURATION_MILLIS);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task));
    }
}
