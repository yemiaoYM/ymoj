package com.ymcloud.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.web.judge.JudgeDispatchProperties;
import com.ymcloud.web.judge.JudgeDispatchPublisher;
import com.ymcloud.web.judge.JudgeDispatchState;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 发布只有确认且成功路由后才完成，租约失败、退回、nack 和超时都保留补偿任务。
 */
class JudgeDispatchPublisherTest {

    JudgeDispatchEntityService dispatches;
    JudgeResultService results;
    RabbitTemplate rabbit;
    JudgeDispatchProperties properties;
    JudgeMessageCodec codec;
    JudgeDispatchPublisher publisher;
    JudgeDispatch dispatch;
    List<Map<String, Object>> updates;
    boolean claimed = true;

    @BeforeEach
    void prepare() {
        dispatches = mock(JudgeDispatchEntityService.class);
        results = mock(JudgeResultService.class);
        rabbit = mock(RabbitTemplate.class);
        properties = new JudgeDispatchProperties();
        properties.setConfirmTimeout(Duration.ofMillis(5));
        codec = new JudgeMessageCodec(new ObjectMapper());
        publisher = createJudgeDispatchPublisher(dispatches, results, rabbit, codec, properties,
                JudgeProtocol.EXCHANGE, JudgeProtocol.TASK_ROUTE);
        updates = new ArrayList<>();
        doAnswer(invocation -> update()).when(dispatches).lambdaUpdate();
        JudgeTask task = new JudgeTask();
        task.setSubmissionId(100L);
        task.setExecutionId(UUID.randomUUID().toString());
        task.setDeadlineEpochMs(System.currentTimeMillis() + 60_000);
        dispatch = new JudgeDispatch();
        dispatch.setId(1L);
        dispatch.setSubmissionId(task.getSubmissionId());
        dispatch.setExecutionId(task.getExecutionId());
        dispatch.setTaskPayload(codec.write(task));
        dispatch.setDeadlineTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        dispatch.setPublishAttempts(0);
        dispatch.setNextAttemptTime(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void ackWithoutReturnMarksWaitingAndCarriesPersistentDeadlineMessage() {
        doAnswer(invocation -> {
            MessagePostProcessor processor = invocation.getArgument(3);
            Message message = processor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertEquals(dispatch.getExecutionId(), message.getMessageProperties().getMessageId());
            assertEquals("application/json", message.getMessageProperties().getContentType());
            assertNotNull(message.getMessageProperties().getExpiration());
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        publisher.publish(dispatch);
        assertEquals(JudgeDispatchState.WAITING, updates.get(1).get("getDispatchState"));
        assertNull(updates.get(1).get("getLeaseToken"));
        verifyNoInteractions(results);
    }

    @Test
    void nackDoesNotMarkTaskAsPublished() {
        brokerConfirmation(false, false);
        publisher.publish(dispatch);
        assertEquals(2, updates.size());
        assertFalse(updates.get(1).containsKey("getDispatchState"));
        assertTrue(updates.get(1).get("getNextAttemptTime") instanceof LocalDateTime);
    }

    @Test
    void unroutableAckIsRetriedInsteadOfLosingTask() {
        brokerConfirmation(true, true);
        publisher.publish(dispatch);
        assertFalse(updates.get(1).containsKey("getDispatchState"));
        verifyNoInteractions(results);
    }

    @Test
    void missingConfirmationAndConnectionFailureAreBothCompensated() {
        publisher.publish(dispatch);
        assertFalse(updates.get(1).containsKey("getDispatchState"));
        updates.clear();
        doThrow(new IllegalStateException("broker unavailable")).when(rabbit)
                .convertAndSend(anyString(), anyString(), any(),
                        any(MessagePostProcessor.class), any(CorrelationData.class));
        publisher.publish(dispatch);
        assertFalse(updates.get(1).containsKey("getDispatchState"));
    }

    @Test
    void anotherPublisherOwningLeasePreventsDuplicatePublish() {
        claimed = false;
        publisher.publish(dispatch);
        verifyNoInteractions(rabbit, results);
        assertEquals(1, updates.size());
    }

    @Test
    void budgetExhaustionCreatesFinalErrorWithoutFurtherSending() {
        dispatch.setPublishAttempts(properties.getMaxPublishAttempts());
        publisher.publish(dispatch);
        verify(results).failPublishing(eq(100L), eq(dispatch.getExecutionId()), anyString());
        verifyNoInteractions(rabbit);
    }

    @Test
    void expiredTaskNeverPublishesPastItsOriginalDeadline() {
        dispatch.setDeadlineTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        publisher.publish(dispatch);
        verify(results).expire(100L, dispatch.getExecutionId());
        verifyNoInteractions(rabbit);
    }

    @Test
    void corruptedPayloadAndVersionMismatchBecomeBoundedFailureWithoutSending() {
        dispatch.setTaskPayload("null");
        publisher.publish(dispatch);
        verify(results).failPublishing(100L, dispatch.getExecutionId(), "持久评测任务格式无效");
        verifyNoInteractions(rabbit);
        reset(results);
        JudgeTask task = new JudgeTask();
        task.setSubmissionId(dispatch.getSubmissionId());
        task.setExecutionId(dispatch.getExecutionId());
        task.setTestDataVersionId(99L);
        task.setDeadlineEpochMs(System.currentTimeMillis() + 60_000);
        dispatch.setTaskPayload(codec.write(task));
        publisher.publish(dispatch);
        verify(results).failPublishing(100L, dispatch.getExecutionId(), "持久评测任务格式无效");
        verifyNoInteractions(rabbit);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistentOutboxFailureLogsOnceAndRecoveryNeverIncludesExceptionDetails() {
        Logger logger = (Logger) LoggerFactory.getLogger(JudgeDispatchPublisher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new IllegalStateException("private-database-secret")).when(dispatches).lambdaQuery();
            publisher.tick();
            publisher.tick();
            publisher.tick();
            assertEquals(1, appender.list.size());
            LambdaQueryChainWrapper<JudgeDispatch> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
            when(query.eq(any(), any())).thenReturn(query);
            when(query.ne(any(), any())).thenReturn(query);
            when(query.le(any(), any())).thenReturn(query);
            when(query.gt(any(), any())).thenReturn(query);
            when(query.and(any())).thenReturn(query);
            when(query.orderByAsc(any(SFunction.class))).thenReturn(query);
            when(query.last(anyString())).thenReturn(query);
            when(query.list()).thenReturn(List.of());
            doReturn(query).when(dispatches).lambdaQuery();
            publisher.tick();
            assertEquals(2, appender.list.size());
            assertTrue(appender.list.get(1).getFormattedMessage().contains("已恢复"));
            doThrow(new IllegalStateException("private-broker-secret")).when(rabbit)
                    .convertAndSend(anyString(), anyString(), any(),
                            any(MessagePostProcessor.class), any(CorrelationData.class));
            publisher.publish(dispatch);
            String failure = appender.list.get(2).getFormattedMessage();
            assertTrue(failure.contains(dispatch.getExecutionId()));
            assertTrue(failure.contains("IllegalStateException"));
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains("private-")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void brokerConfirmation(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                        312, "NO_ROUTE", JudgeProtocol.EXCHANGE, JudgeProtocol.TASK_ROUTE));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateChainWrapper<JudgeDispatch> update() {
        Map<String, Object> values = new LinkedHashMap<>();
        updates.add(values);
        LambdaUpdateChainWrapper<JudgeDispatch> update = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.le(any(), any())).thenReturn(update);
        when(update.and(any())).thenReturn(update);
        when(update.update()).thenAnswer(invocation -> claimed);
        doAnswer(invocation -> {
            SFunction<JudgeDispatch, ?> field = invocation.getArgument(0);
            values.put(LambdaUtils.extract(field).getImplMethodName(), invocation.getArgument(1));
            return update;
        }).when(update).set(any(), nullable(Object.class));
        return update;
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param dispatches 任务箱实体服务
     * @param results 结果落库入口
     * @param rabbitTemplate 可控制确认和退回的消息模板
     * @param codec 严格判题消息编解码器
     * @param properties 本测试使用的容量、截止或发布配置
     * @param exchange 测试交换机名
     * @param routingKey 测试任务路由
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeDispatchPublisher createJudgeDispatchPublisher(
            JudgeDispatchEntityService dispatches,
            JudgeResultService results,
            RabbitTemplate rabbitTemplate,
            JudgeMessageCodec codec,
            JudgeDispatchProperties properties,
            String exchange,
            String routingKey) {
        JudgeDispatchPublisher component = new JudgeDispatchPublisher();
        ReflectionTestUtils.setField(component, "dispatches", dispatches);
        ReflectionTestUtils.setField(component, "results", results);
        ReflectionTestUtils.setField(component, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(component, "codec", codec);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "exchange", exchange);
        ReflectionTestUtils.setField(component, "routingKey", routingKey);
        return component;
    }
}
