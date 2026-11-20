package com.ymcloud.web.judge;

import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.RabbitMQConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 声明 Web 与 Worker 共用的判题拓扑、消息模板及结果消费重试
 */
@Slf4j
@Configuration
public class JudgeMessagingConfig {

    /**
     * 声明 Web 与 Worker 共用的持久交换机。
     * @param name 交换机名
     * @return 持久 Topic 交换机
     */
    @Bean("judgeWorkerExchange")
    public TopicExchange exchange(
            @Value("${ymcloud.judge.exchange:" + RabbitMQConstants.JUDGE_EXCHANGE + "}") String name) {
        return new TopicExchange(name, true, false);
    }

    /**
     * 声明任务队列及失败死信路由。
     * @param name 任务队列名
     * @param exchange 交换机名
     * @return 持久任务队列
     */
    @Bean("judgeTaskQueue")
    public Queue taskQueue(
            @Value("${ymcloud.judge.task-queue:" + RabbitMQConstants.JUDGE_QUEUE + "}") String name,
            @Value("${ymcloud.judge.exchange:" + RabbitMQConstants.JUDGE_EXCHANGE + "}") String exchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey(RabbitMQConstants.JUDGE_DEAD_ROUTING_KEY)
                .build();
    }

    /**
     * 声明持久结果队列。
     * @param name 结果队列名
     * @return 持久结果队列
     */
    @Bean("judgeResultQueue")
    public Queue resultQueue(
            @Value("${ymcloud.judge.result-queue:" + RabbitMQConstants.JUDGE_RESULT_QUEUE + "}") String name) {
        return QueueBuilder.durable(name).build();
    }

    /**
     * 声明三秒延时重试队列，过期后回到任务路由。
     * @param name 任务队列名
     * @param exchange 交换机名
     * @param route 任务路由
     * @return 具有 TTL 的重试队列
     */
    @Bean("judgeTaskRetryQueue")
    public Queue retryQueue(
            @Value("${ymcloud.judge.task-queue:" + RabbitMQConstants.JUDGE_QUEUE + "}") String name,
            @Value("${ymcloud.judge.exchange:" + RabbitMQConstants.JUDGE_EXCHANGE + "}") String exchange,
            @Value("${ymcloud.judge.task-routing-key:" + RabbitMQConstants.JUDGE_ROUTING_KEY + "}") String route) {
        return QueueBuilder.durable(name + ".retry")
                .ttl(3000)
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey(route)
                .build();
    }

    /**
     * 声明保留失败任务的死信队列。
     * @param name 任务队列名
     * @return 持久死信队列
     */
    @Bean("judgeTaskDeadQueue")
    public Queue deadQueue(
            @Value("${ymcloud.judge.task-queue:" + RabbitMQConstants.JUDGE_QUEUE + "}") String name) {
        return QueueBuilder.durable(name + ".dead").build();
    }

    /**
     * 绑定任务队列到指定路由。
     * @param queue 任务队列
     * @param exchange 判题交换机
     * @param route 任务路由
     * @return 任务绑定
     */
    @Bean("judgeTaskBinding")
    public Binding taskBinding(
            @Qualifier("judgeTaskQueue") Queue queue,
            @Qualifier("judgeWorkerExchange") TopicExchange exchange,
            @Value("${ymcloud.judge.task-routing-key:" + RabbitMQConstants.JUDGE_ROUTING_KEY + "}") String route) {
        return BindingBuilder.bind(queue).to(exchange).with(route);
    }

    /**
     * 绑定结果队列到指定路由。
     * @param queue 结果队列
     * @param exchange 判题交换机
     * @param route 结果路由
     * @return 结果绑定
     */
    @Bean("judgeResultBinding")
    public Binding resultBinding(
            @Qualifier("judgeResultQueue") Queue queue,
            @Qualifier("judgeWorkerExchange") TopicExchange exchange,
            @Value("${ymcloud.judge.result-routing-key:" + RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY + "}") String route) {
        return BindingBuilder.bind(queue).to(exchange).with(route);
    }

    /**
     * 绑定重试队列到协议固定路由。
     * @param queue 重试队列
     * @param exchange 判题交换机
     * @return 重试绑定
     */
    @Bean("judgeTaskRetryBinding")
    public Binding retryBinding(@Qualifier("judgeTaskRetryQueue") Queue queue,
                                 @Qualifier("judgeWorkerExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(RabbitMQConstants.JUDGE_RETRY_ROUTING_KEY);
    }

    /**
     * 绑定死信队列到协议固定路由。
     * @param queue 死信队列
     * @param exchange 判题交换机
     * @return 死信绑定
     */
    @Bean("judgeTaskDeadBinding")
    public Binding deadBinding(@Qualifier("judgeTaskDeadQueue") Queue queue,
                                @Qualifier("judgeWorkerExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(RabbitMQConstants.JUDGE_DEAD_ROUTING_KEY);
    }

    /**
     * 创建正式判题与 SPJ 编译共用的消息模板
     * 回复超时仅用于请求响应，正式任务的发布确认由发布器单独控制
     * @param connectionFactory 消息连接工厂
     * @return 不使用 Java 类型头反序列化的模板
     */
    @Bean("judgeRabbitTemplate")
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new SimpleMessageConverter());
        template.setMandatory(true);
        template.setReplyTimeout(JudgeConstants.SPJ_COMPILE_TIMEOUT_MILLIS);
        return template;
    }

    /**
     * 配置有界结果重试，耗尽后拒绝消息并等待任务箱恢复。
     * @param connectionFactory 消息连接工厂
     * @return 结果消费者工厂
     */
    @Bean("judgeResultListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory listenerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2, 3000)
                .recoverer((message, cause) -> {
                    log.warn("判题结果处理重试耗尽，等待任务箱恢复，不记录结果正文");
                    // 原始异常可能包含 SQL 参数或私有输出，框架日志只保留安全的错误说明
                    throw new AmqpRejectAndDontRequeueException("判题结果处理失败");
                })
                .build());
        return factory;
    }
}
