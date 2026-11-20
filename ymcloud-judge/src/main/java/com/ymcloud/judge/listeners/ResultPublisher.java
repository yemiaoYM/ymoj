package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.constant.JudgeConstants;
import com.ymcloud.pojo.bo.judge.JudgeResult;
import com.ymcloud.pojo.bo.judge.JudgeTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 持久化 JSON 消息必须同时取得 publisher confirm 且未被退回。
 */
@Component
@Slf4j
public class ResultPublisher {

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private WorkerProperties properties;

    @Value("${ymcloud.judge.exchange:" + JudgeConstants.JUDGE_EXCHANGE + "}")
    private String exchange;

    @Value("${ymcloud.judge.result-routing-key:" + JudgeConstants.JUDGE_RESULT_ROUTING_KEY + "}")
    private String resultRoute;

    /**
     * 发布进度或终态，要求 Broker ACK 且未退回后才报告成功。
     * @param result 当前执行的结果
     */
    public void publish(JudgeResult result) {
        try {
            long elapsed = send(result, resultRoute);
            if (result.isTerminal()) {
                log.info("Worker 终态消息确认成功 submission={} execution={} status={} elapsedMs={}",
                        result.getSubmissionId(), result.getExecutionId(),
                        result.getStatus(), elapsed);
            } else {
                log.debug("Worker 进度消息确认成功 submission={} execution={} sequence={} status={} elapsedMs={}",
                        result.getSubmissionId(), result.getExecutionId(),
                        result.getSequence(), result.getStatus(), elapsed);
            }
        } catch (RuntimeException e) {
            log.warn("Worker 结果消息确认失败 submission={} execution={} sequence={} exception={}",
                    result.getSubmissionId(), result.getExecutionId(),
                    result.getSequence(), failureType(e));
            throw e;
        }
    }

    /**
     * 发布有界延时重试消息，确认失败继续向调用方抛出。
     * @param task 保持原身份和数据快照的任务
     */
    public void retry(JudgeTask task) {
        try {
            long elapsed = send(task, JudgeConstants.JUDGE_RETRY_ROUTING_KEY);
            log.warn("Worker 延时重试消息确认成功 submission={} execution={} elapsedMs={}",
                    task.getSubmissionId(), task.getExecutionId(), elapsed);
        } catch (RuntimeException e) {
            log.warn("Worker 延时重试消息确认失败 submission={} execution={} exception={}",
                    task.getSubmissionId(), task.getExecutionId(), failureType(e));
            throw e;
        }
    }

    /**
     * 发送持久 JSON 并等待关联确认，恢复线程中断信号。
     * @param payload 任务或结果契约对象
     * @param route 固定消息路由
     * @return 确认耗时，毫秒
     */
    private long send(Object payload, String route) {
        long started = System.nanoTime();
        try {
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding("UTF-8");
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
            Message message = new Message(mapper.writeValueAsBytes(payload), messageProperties);
            rabbit.send(exchange, route, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(properties.getConfirmTimeoutMillis(),
                    TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("判题消息未被队列接收");
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("判题消息确认被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("判题消息发布失败", e);
        }
    }

    /**
     * 仅提取安全异常类型，避免日志带入消息私有正文。
     * @param exception 发布异常
     * @return 直接原因或异常本身的类型名称
     */
    private String failureType(RuntimeException exception) {
        Throwable cause = exception.getCause();
        return cause == null ? exception.getClass().getSimpleName() : cause.getClass().getSimpleName();
    }
}
