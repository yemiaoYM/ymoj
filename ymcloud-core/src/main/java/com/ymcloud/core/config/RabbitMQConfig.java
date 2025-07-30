package com.ymcloud.core.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @PackageName: com.ymcloud.core.config
 * @ClassName: MqConfig
 * @Author: Yemiao
 * @CreateTime: 2025-07-27  13:19
 * @Description: RabbitMQ配置
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 消息转化器
     */
    @Bean
    public MessageConverter Jackson2JsonMessageConverter() {
        Jackson2JsonMessageConverter jsonMessageConverter = new Jackson2JsonMessageConverter();
        //消息自动生成唯一标识id
        //采用的是UUID的生成方式，用于识别不用的消息，也可以在业务中基于ID判断是否是重复消息
        jsonMessageConverter.setCreateMessageIds(true);
        return jsonMessageConverter;
    }

}
