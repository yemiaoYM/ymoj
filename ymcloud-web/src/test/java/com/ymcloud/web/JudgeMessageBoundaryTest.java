package com.ymcloud.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 协议只使用数字状态与明确 DTO，非法 UTF-8 和任意类型头不能穿透消息边界。
 */
class JudgeMessageBoundaryTest {

    /**
     * 严格拒绝任务身份、结果状态和布尔值的隐式转换，且不改变全局映射器
     * @throws Exception 全局映射器校验失败
     */
    @Test
    void numericAndBooleanCoercionIsRejectedOnlyAtJudgeBoundary() throws Exception {
        ObjectMapper applicationMapper = new ObjectMapper();
        JudgeMessageCodec codec = new JudgeMessageCodec(applicationMapper);
        assertThrows(IllegalArgumentException.class, () -> codec.task("{\"submissionId\":1.9}"));
        assertThrows(IllegalArgumentException.class, () -> codec.task("{\"submissionId\":\"1\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.result("{\"terminal\":null}"));
        assertThrows(IllegalArgumentException.class, () -> codec.result("{\"status\":0.9}"));
        assertThrows(IllegalArgumentException.class, () -> codec.result("{\"status\":\"0\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.result("{\"terminal\":\"true\"}"));
        assertEquals(1L, applicationMapper.readValue("{\"submissionId\":\"1\"}", JudgeTask.class).getSubmissionId());
    }

    /**
     * 全局 HTTP 忽略未知字段时，判题消息仍拒绝非契约字段且不修改全局配置
     * @throws Exception 全局映射器解析失败
     */
    @Test
    void unknownFieldsAreRejectedRegardlessOfHttpMapperConfiguration() throws Exception {
        ObjectMapper applicationMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        JudgeMessageCodec codec = new JudgeMessageCodec(applicationMapper);
        String task = "{\"submissionId\":1,\"unexpected\":true}";
        assertThrows(IllegalArgumentException.class, () -> codec.task(task));
        assertThrows(IllegalArgumentException.class, () -> codec.result("{\"unexpected\":true}"));
        assertThrows(IllegalArgumentException.class, () -> codec.spjResult("{\"unexpected\":true}"));
        assertEquals(1L, applicationMapper.readValue(task, JudgeTask.class).getSubmissionId());
    }

    /**
     * 拒绝消息尾部的第二份 JSON，避免静默忽略额外的协议数据。
     */
    @Test
    void trailingJsonIsRejectedForTasksAndResults() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> codec.task("{} {}"));
        assertThrows(IllegalArgumentException.class, () -> codec.result("{} []"));
    }

    /**
     * 重复身份字段不得以后值覆盖前值，也不能交给结果服务。
     */
    @Test
    void duplicateIdentityFieldsAreRejectedBeforeConsumerInvocation() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        String payload = "{\"submissionId\":1,\"submissionId\":2}";
        assertThrows(IllegalArgumentException.class, () -> codec.task(payload));
        JudgeResultService results = mock(JudgeResultService.class);
        JudgeResultListener listener = createJudgeResultListener(codec, results);
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
        createJudgeResultListener(codec, results).receive(message);
        ArgumentCaptor<JudgeResult> parsed = ArgumentCaptor.forClass(JudgeResult.class);
        verify(results).receive(parsed.capture());
        assertEquals(result.getExecutionId(), parsed.getValue().getExecutionId());
    }

    @Test
    void malformedUtf8AndUnknownStatusAreRejectedWithoutConsumerInvocation() {
        JudgeMessageCodec codec = new JudgeMessageCodec(new ObjectMapper());
        JudgeResultService results = mock(JudgeResultService.class);
        JudgeResultListener listener = createJudgeResultListener(codec, results);
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
        JudgeResultListener listener = createJudgeResultListener(codec, results);
        Message message = new Message("null".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message));
        assertThrows(IllegalArgumentException.class, () -> codec.task("null"));
        assertThrows(IllegalArgumentException.class, () -> codec.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> codec.write(null));
        verifyNoInteractions(results);
    }

    /**
     * 实体诊断只输出明确允许的字段，源码、任务正文和结果正文均不可见。
     */
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

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param codec 严格判题消息编解码器
     * @param results 结果落库入口
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeResultListener createJudgeResultListener(JudgeMessageCodec codec, JudgeResultService results) {
        JudgeResultListener component = new JudgeResultListener();
        ReflectionTestUtils.setField(component, "codec", codec);
        ReflectionTestUtils.setField(component, "results", results);
        return component;
    }
}
