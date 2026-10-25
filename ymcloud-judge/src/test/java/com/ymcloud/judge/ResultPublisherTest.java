package com.ymcloud.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.ResultPublisher;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 验证消息确认、退回、超时与重试路由。
 */
class ResultPublisherTest {

    @Test
    void confirmedResultsArePersistentUtf8MessagesAndRetryUsesSeparateRoute() throws Exception {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        AtomicReference<Message> sent = new AtomicReference<>();
        AtomicReference<String> route = new AtomicReference<>();
        doAnswer(invocation -> {
            route.set(invocation.getArgument(1));
            sent.set(invocation.getArgument(2));
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("exchange"), any(String.class), any(Message.class), any(CorrelationData.class));
        var mapper = new WorkerAmqpConfig().workerObjectMapper();
        ResultPublisher publisher = createResultPublisher(rabbit, mapper,
                new WorkerProperties(), "exchange", "judge.result");
        JudgeResult result = JudgeResult.builder().terminal(true).status(JudgeVerdict.STATUS_COMPILE_ERROR)
                .errorMessage("编译错误").build();

        assertDoesNotThrow(() -> publisher.publish(result));

        assertEquals("judge.result", route.get());
        assertEquals(MessageDeliveryMode.PERSISTENT, sent.get().getMessageProperties().getDeliveryMode());
        assertEquals("UTF-8", sent.get().getMessageProperties().getContentEncoding());
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, sent.get().getMessageProperties().getContentType());
        assertEquals(result, mapper.readValue(sent.get().getBody(), JudgeResult.class));

        publisher.retry(WorkerFixtures.task(1, 0));

        assertEquals("judge.retry", route.get());
    }

    @Test
    void missingConfirmTimesOutAndInterruptedWaitPreservesInterruptFlag() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        WorkerProperties properties = new WorkerProperties();
        properties.setConfirmTimeoutMillis(25L);
        ResultPublisher publisher = createResultPublisher(rabbit, new WorkerAmqpConfig().workerObjectMapper(),
                properties, "exchange", "judge.result");
        JudgeResult result = JudgeResult.builder().terminal(true).status(JudgeVerdict.STATUS_ACCEPTED).build();

        IllegalStateException timeout = assertThrows(IllegalStateException.class, () -> publisher.publish(result));
        assertInstanceOf(TimeoutException.class, timeout.getCause());

        Thread.currentThread().interrupt();
        try {
            IllegalStateException interrupted = assertThrows(IllegalStateException.class,
                    () -> publisher.publish(result));
            assertInstanceOf(InterruptedException.class, interrupted.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void publisherRejectsNackOrReturnedMessageEvenWhenConfirmIsAck() {
        for (boolean returned : new boolean[]{false, true}) {
            RabbitTemplate rabbit = mock(RabbitTemplate.class);
            doAnswer(invocation -> {
                CorrelationData correlation = invocation.getArgument(3);
                if (returned) {
                    correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312,
                            "NO_ROUTE", "exchange", "judge.result"));
                }
                correlation.getFuture().complete(new CorrelationData.Confirm(returned, null));
                return null;
            }).when(rabbit).send(eq("exchange"), eq("judge.result"), any(Message.class), any(CorrelationData.class));
            ResultPublisher publisher = createResultPublisher(rabbit, new WorkerAmqpConfig().workerObjectMapper(),
                    new WorkerProperties(), "exchange", "judge.result");
            assertThrows(IllegalStateException.class, () -> publisher.publish(
                    JudgeResult.builder().terminal(true).status(JudgeVerdict.STATUS_ACCEPTED).build()));
        }
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param rabbit 可控制确认和退回的消息模板
     * @param mapper 判题专用 JSON 映射器
     * @param properties 本测试使用的容量、截止或发布配置
     * @param exchange 测试交换机名
     * @param resultRoute 测试结果路由
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static ResultPublisher createResultPublisher(
            RabbitTemplate rabbit,
            ObjectMapper mapper,
            WorkerProperties properties,
            String exchange,
            String resultRoute) {
        ResultPublisher component = new ResultPublisher();
        ReflectionTestUtils.setField(component, "rabbit", rabbit);
        ReflectionTestUtils.setField(component, "mapper", mapper);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "exchange", exchange);
        ReflectionTestUtils.setField(component, "resultRoute", resultRoute);
        return component;
    }
}
