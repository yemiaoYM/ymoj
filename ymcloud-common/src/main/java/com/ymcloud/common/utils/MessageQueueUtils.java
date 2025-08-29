package com.ymcloud.common.utils;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @PackageName: com.ymcloud.common.utils
 * @ClassName: MessageQueueUtils
 * @Author: Yemiao
 * @CreateTime: 2025-08-29  15:21
 * @Description: 消息队列发送工具类
 */
@Component
public class MessageQueueUtils {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送普通消息
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息内容
     */
    public <T> void send(String exchange, String routingKey, T message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

    /**
     * 发送延迟消息
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息内容
     * @param delayMs    延迟时间（毫秒）
     */
    public <T> void sendWithDelay(String exchange, String routingKey, T message, long delayMs) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
            msg.getMessageProperties().setDelayLong(delayMs);
            return msg;
        });
    }

    /**
     * 批量发送消息
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param messages   消息数组
     */
    public <T> void sendBatch(String exchange, String routingKey, List<T> messages) {
        for (T message : messages) {
            send(exchange, routingKey, message);
        }
    }

}
