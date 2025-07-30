package com.ymcloud.judge.listeners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @PackageName: com.ymcloud.judge.listeners
 * @ClassName: JudgeListener
 * @Author: Yemiao
 * @CreateTime: 2025-07-27  13:23
 * @Description: 评测监听
 */
@Slf4j
@Component
public class JudgeListener {

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "object.queue",durable = "true"),
            exchange = @Exchange(value = "judge.topic",type = ExchangeTypes.TOPIC),
            key = "judge.success"
    ))
    public void listen(Map<String, Object> msg) {
        log.info("消费者监听到评测消息：{}",msg);

        //业务中尽量不要抛异常，不然会启动重试机制，发送到error队列中
    }

}
