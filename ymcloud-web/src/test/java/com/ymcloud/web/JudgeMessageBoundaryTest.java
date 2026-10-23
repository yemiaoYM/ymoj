package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeDispatch;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeMessagingConfig;
import com.ymcloud.web.judge.JudgeResultListener;
import com.ymcloud.web.judge.JudgeResultService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-12
 * @Description: 协议只使用数字状态与明确 DTO，非法 UTF-8 和任意类型头不能穿透消息边界。
 */
class JudgeMessageBoundaryTest {

    /** 拒绝消息尾部的第二份 JSON，避免静默忽略额外的协议数据。 */
    @Test
    void trailingJsonIsRejectedForTasksAndResults() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> codec.task("{} {}"));
        assertThrows(IllegalArgumentException.class, () -> codec.result("{} []"));
    }

    /** 重复身份字段不得以后值覆盖前值，也不能交给结果服务。 */
    @Test
    void duplicateIdentityFieldsAreRejectedBeforeConsumerInvocation() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        String payload = "{\"submissionId\":1,\"submissionId\":2}";
        assertThrows(IllegalArgumentException.class, () -> codec.task(payload));
        JudgeResultService results = mock(JudgeResultService.class);
        JudgeResultListener listener = new JudgeResultListener(codec, results);
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), new MessageProperties());
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message));
        verifyNoInteractions(results);
    }

    @Test
    void taskLimitCountsUtf8BytesAndJsonEscapesBeforePublishing() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JudgeMessageCodec codec = new JudgeMessageCodec(mapper);
        JudgeTask task = new JudgeTask();
        task.setInput("中".repeat(256 * 1024));
        String allowed = codec.write(task);
        assertEquals(task.getInput(), codec.task(allowed).getInput());
        task.setInput("中".repeat(1024 * 1024));
        String multibyte = mapper.writeValueAsString(task);
        assertTrue(multibyte.length() < 3 * 1024 * 1024);
        assertTrue(multibyte.getBytes(StandardCharsets.UTF_8).length > 3 * 1024 * 1024);
        assertThrows(IllegalArgumentException.class, () -> codec.write(task));
        assertThrows(IllegalArgumentException.class, () -> codec.task(multibyte));
        task.setInput("\0".repeat(600 * 1024));
        assertTrue(task.getInput().length() < 1024 * 1024);
        assertThrows(IllegalArgumentException.class, () -> codec.write(task));
    }

    @Test
    void resultLimitAlsoCountsUtf8BytesRatherThanCharacters() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JudgeMessageCodec codec = new JudgeMessageCodec(mapper);
        JudgeResult result = new JudgeResult();
        result.setErrorMessage("中".repeat(6 * 1024 * 1024));
        String payload = mapper.writeValueAsString(result);
        assertTrue(payload.length() < 16 * 1024 * 1024);
        assertTrue(payload.getBytes(StandardCharsets.UTF_8).length > 16 * 1024 * 1024);
        assertThrows(IllegalArgumentException.class, () -> codec.write(result));
        assertThrows(IllegalArgumentException.class, () -> codec.result(payload));
    }

    @Test
    void javaTypeHeaderIsIgnoredAndVerdictUsesNumericWireCode() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        JudgeResult result = new JudgeResult();
        result.setSubmissionId(100L);
        result.setExecutionId(UUID.randomUUID().toString());
        result.setStatus(JudgeVerdict.STATUS_ACCEPTED);
        String payload = codec.write(result);
        assertTrue(payload.contains("\"status\":0"));
        MessageProperties properties = new MessageProperties();
        properties.setHeader("__TypeId__", "java.lang.ProcessBuilder");
        JudgeResultService results = mock(JudgeResultService.class);
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
        new JudgeResultListener(codec, results).receive(message);
        ArgumentCaptor<JudgeResult> parsed = ArgumentCaptor.forClass(JudgeResult.class);
        verify(results).receive(parsed.capture());
        assertEquals(result.getExecutionId(), parsed.getValue().getExecutionId());
    }

    @Test
    void malformedUtf8AndUnknownStatusAreRejectedWithoutConsumerInvocation() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        JudgeResultService results = mock(JudgeResultService.class);
        JudgeResultListener listener = new JudgeResultListener(codec, results);
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.receive(new Message(new byte[]{(byte) 0xc3, 0x28}, new MessageProperties())));
        Message unknownStatus = new Message("{\"status\":999}".getBytes(StandardCharsets.UTF_8),
                new MessageProperties());
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(unknownStatus));
        verifyNoInteractions(results);
    }

    @Test
    void jsonNullIsRejectedBeforeResultConsumerAndUnsupportedObjectsCannotBeSerialized() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        JudgeResultService results = mock(JudgeResultService.class);
        JudgeResultListener listener = new JudgeResultListener(codec, results);
        Message message = new Message("null".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message));
        assertThrows(IllegalArgumentException.class, () -> codec.task("null"));
        assertThrows(IllegalArgumentException.class, () -> codec.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> codec.write(null));
        verifyNoInteractions(results);
    }

    /** 实体诊断只输出明确允许的字段，源码、任务正文和结果正文均不可见。 */
    @Test
    void entityLoggingNeverIncludesCodePrivateTaskOutput() {
        Judge judge = new Judge();
        judge.setId(100L);
        judge.setCode("private-source-marker");
        judge.setErrorMessage("private-error-marker");
        JudgeDispatch dispatch = new JudgeDispatch();
        dispatch.setId(1L);
        dispatch.setSubmissionId(100L);
        dispatch.setTaskPayload("private-task-marker");
        dispatch.setResultPayload("private-result-marker");
        String diagnostic = judge + " " + dispatch;
        assertTrue(diagnostic.contains("100"));
        assertFalse(diagnostic.contains("private-"));
    }

    @Test
    void taskRetryAndDeadTopologyHasSameArgumentsAsWorker() {
        JudgeMessagingConfig config = new JudgeMessagingConfig();
        Queue task = config.taskQueue("test.task", "test.exchange");
        Queue retry = config.retryQueue("test.task", "test.exchange", "test.judge");
        assertEquals("test.exchange", task.getArguments().get("x-dead-letter-exchange"));
        assertEquals("judge.dead", task.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("test.task.retry", retry.getName());
        assertEquals(3000, retry.getArguments().get("x-message-ttl"));
        assertEquals("test.judge", retry.getArguments().get("x-dead-letter-routing-key"));
        assertTrue(config.resultQueue("test.result").getArguments().isEmpty());
        assertFalse(config.exchange("test.exchange").isDelayed());
    }
}
