package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 持久化 JSON 消息必须同时取得 publisher confirm 且未被退回。
 */
@Component
public class ResultPublisher {

    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final WorkerProperties properties;
    private final String exchange;
    private final String resultRoute;

    public ResultPublisher(RabbitTemplate rabbit, ObjectMapper mapper, WorkerProperties properties,
                           @Value("${ymcloud.judge.exchange:" + JudgeProtocol.EXCHANGE + "}") String exchange,
                           @Value("${ymcloud.judge.result-routing-key:judge.result}") String resultRoute) {
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.properties = properties;
        this.exchange = exchange;
        this.resultRoute = resultRoute;
    }

    public void publish(JudgeResult result) {
        send(result, resultRoute);
    }

    public void retry(JudgeTask task) {
        send(task, JudgeProtocol.RETRY_ROUTE);
    }

    private void send(Object payload, String route) {
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("判题消息确认被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("判题消息发布失败", e);
        }
    }
}
