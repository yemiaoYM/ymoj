package com.ymcloud.core.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @PackageName: com.ymcloud.core.config
 * @ClassName: RabbitMQErrorConfig
 * @Author: Yemiao
 * @CreateTime: 2025-07-28  22:13
 * @Description: mq超过尝试错误次数异常处理类
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.rabbitmq.listener.simple.retry",name = "enabled",havingValue = "true")
public class RabbitMQErrorConfig {

    /**
     * 创建定向交换机
     */
    @Bean
    public DirectExchange errorExchange() {
        return new DirectExchange("error.direct");
    }

    /**
     * 创建队列
     */
    @Bean
    public Queue errorQueue() {
        return new Queue("error.queue");
    }

    /**
     * 将交换机和队列进行绑定
     */
    @Bean
    public Binding errorBinding() {
        return BindingBuilder.bind(errorQueue()).to(errorExchange()).with("error");
    }

    /**
     * 将错误消息都发送到错误队列，后期进行统一处理，人工处理
     */
    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate,"error.direct","error");
    }

}
