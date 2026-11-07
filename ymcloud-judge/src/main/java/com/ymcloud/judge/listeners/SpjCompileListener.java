package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.SpjCompileRequest;
import com.ymcloud.pojo.contract.judge.SpjCompileResult;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: 接收有界 SPJ 编译预检，通过沙箱执行后返回编译诊断
 */
@Component
public class SpjCompileListener {

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private GoJudgeExecutor executor;

    /**
     * 校验独立消息边界并返回编译结果，过期或非法请求不执行
     * @param message 编译请求消息
     * @return 自动发送到请求 reply-to 的响应
     */
    @RabbitListener(queues = SpjProgram.COMPILE_QUEUE, containerFactory = "spjListenerFactory")
    public Message compile(Message message) {
        SpjCompileRequest request;
        try {
            if (message.getBody().length > 512 * 1024) {
                throw new IllegalArgumentException("编译请求超限");
            }
            request = mapper.readValue(message.getBody(), SpjCompileRequest.class);
            long now = System.currentTimeMillis();
            if (request == null || request.requestId() == null
                    || !UUID.fromString(request.requestId()).toString().equals(request.requestId())
                    || request.deadlineEpochMs() <= now
                    || request.deadlineEpochMs() > now + SpjProgram.COMPILE_TIMEOUT_MILLIS
                    + JudgeProtocol.MAX_CLOCK_SKEW_MILLIS
                    || request.program() == null) {
                throw new IllegalArgumentException("编译请求身份或截止时间无效");
            }
            request.program().validate();
        } catch (IOException | IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("SPJ 编译请求无效");
        }
        JudgeTask task = new JudgeTask();
        task.setExecutionId(request.requestId());
        task.setDeadlineEpochMs(request.deadlineEpochMs());
        SpjCompileResult result;
        try (GoJudgeTaskSession session = executor.openTask(task)) {
            executor.compileSpj(session, request.program());
            result = new SpjCompileResult(request.requestId(), request.program().getDigest(), true, "编译通过");
        } catch (CompileException e) {
            result = new SpjCompileResult(request.requestId(), request.program().getDigest(), false, e.getMessage());
        } catch (IOException e) {
            result = new SpjCompileResult(request.requestId(), request.program().getDigest(), false,
                    "SPJ 编译服务暂不可用，请稍后重新编译");
        }
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return new Message(mapper.writeValueAsBytes(result), properties);
        } catch (IOException e) {
            throw new AmqpRejectAndDontRequeueException("SPJ 编译响应序列化失败");
        }
    }
}
