package com.ymcloud.judge.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * 普通持久化 Topic 拓扑，使用 TTL 重试队列，无 delayed 插件依赖。
 */
@Configuration
public class WorkerAmqpConfig {

    @Bean
    public ObjectMapper workerObjectMapper() {
        return new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(32)
                        .maxStringLength(2 * 1024 * 1024)
                        .build())
                .build()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    public Declarables workerTopology(
            @Value("${ymcloud.judge.exchange:" + JudgeProtocol.EXCHANGE + "}") String exchangeName,
            @Value("${ymcloud.judge.task-queue:" + JudgeProtocol.TASK_QUEUE + "}") String taskName,
            @Value("${ymcloud.judge.result-queue:" + JudgeProtocol.RESULT_QUEUE + "}") String resultName,
            @Value("${ymcloud.judge.task-routing-key:judge}") String taskRoute,
            @Value("${ymcloud.judge.result-routing-key:judge.result}") String resultRoute) {
        TopicExchange exchange = new TopicExchange(exchangeName, true, false);
        Queue task = QueueBuilder.durable(taskName)
                .deadLetterExchange(exchangeName)
                .deadLetterRoutingKey(JudgeProtocol.DEAD_ROUTE)
                .build();
        Queue result = QueueBuilder.durable(resultName).build();
        Queue retry = QueueBuilder.durable(taskName + ".retry")
                .ttl(3000)
                .deadLetterExchange(exchangeName)
                .deadLetterRoutingKey(taskRoute)
                .build();
        Queue dead = QueueBuilder.durable(taskName + ".dead").build();
        return new Declarables(exchange, task, result, retry, dead,
                BindingBuilder.bind(task).to(exchange).with(taskRoute),
                BindingBuilder.bind(result).to(exchange).with(resultRoute),
                BindingBuilder.bind(retry).to(exchange).with(JudgeProtocol.RETRY_ROUTE),
                BindingBuilder.bind(dead).to(exchange).with(JudgeProtocol.DEAD_ROUTE));
    }

    @Bean
    public RabbitTemplate workerRabbitTemplate(ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory caching) {
            caching.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            caching.setPublisherReturns(true);
        }
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    @DependsOn("goJudgeStartupCheck")
    public SimpleRabbitListenerContainerFactory workerListenerFactory(ConnectionFactory connectionFactory,
                                                                     WorkerProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConcurrency());
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(true);
        return factory;
    }
}
