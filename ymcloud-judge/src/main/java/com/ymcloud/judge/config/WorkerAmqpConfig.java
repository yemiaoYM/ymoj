package com.ymcloud.judge.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ymcloud.judge.constant.JudgeConstants;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
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
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 配置严格消息解析和持久化 Topic 拓扑，使用 TTL 队列重试。
 */
@Configuration
public class WorkerAmqpConfig {

    /**
     * 编译预检无需恢复任务箱，队列有界且过期请求直接移除
     * @return SPJ 编译请求队列
     */
    @Bean
    public Queue spjCompileQueue() {
        return QueueBuilder.durable(JudgeConstants.SPJ_COMPILE_QUEUE)
                .ttl((int) JudgeConstants.SPJ_COMPILE_TIMEOUT_MILLIS)
                .maxLength(32)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    /**
     * 独立单并发执行编译检查，响应发送完成后由容器确认消息
     * @param connectionFactory 消息连接工厂
     * @param properties 已验证的 Worker 消息确认配置
     * @return 编译请求消费者工厂
     */
    @Bean
    @DependsOn("goJudgeStartupCheck")
    public SimpleRabbitListenerContainerFactory spjListenerFactory(ConnectionFactory connectionFactory,
                                                                   WorkerProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setDefaultRequeueRejected(false);
        factory.setContainerCustomizer(container -> container.setShutdownTimeout(shutdownTimeout(properties)));
        return factory;
    }

    /**
     * 创建 Worker 专用映射器，拒绝重复字段、尾随 JSON 和数值隐式转换。
     * @return 仅用于固定消息及沙箱协议的映射器，不启用任意类型解析
     */
    @Bean
    public ObjectMapper workerObjectMapper() {
        return JsonMapper.builder(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(32)
                        .maxStringLength(2 * 1024 * 1024)
                        .build())
                .build())
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    /**
     * 声明与 Web 一致的任务、结果、延时重试及死信队列。
     * @param exchangeName 持久化 Topic 交换机名
     * @param taskName 任务队列名
     * @param resultName 结果队列名
     * @param taskRoute 任务路由
     * @param resultRoute 结果路由
     * @return 可由 Spring AMQP 声明的完整拓扑
     */
    @Bean
    public Declarables workerTopology(
            @Value("${ymcloud.judge.exchange:" + JudgeConstants.JUDGE_EXCHANGE + "}") String exchangeName,
            @Value("${ymcloud.judge.task-queue:" + JudgeConstants.JUDGE_QUEUE + "}") String taskName,
            @Value("${ymcloud.judge.result-queue:" + JudgeConstants.JUDGE_RESULT_QUEUE + "}") String resultName,
            @Value("${ymcloud.judge.task-routing-key:" + JudgeConstants.JUDGE_ROUTING_KEY + "}") String taskRoute,
            @Value("${ymcloud.judge.result-routing-key:" + JudgeConstants.JUDGE_RESULT_ROUTING_KEY + "}")
            String resultRoute) {
        TopicExchange exchange = new TopicExchange(exchangeName, true, false);
        Queue task = QueueBuilder.durable(taskName)
                .deadLetterExchange(exchangeName)
                .deadLetterRoutingKey(JudgeConstants.JUDGE_DEAD_ROUTING_KEY)
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
                BindingBuilder.bind(retry).to(exchange).with(JudgeConstants.JUDGE_RETRY_ROUTING_KEY),
                BindingBuilder.bind(dead).to(exchange).with(JudgeConstants.JUDGE_DEAD_ROUTING_KEY));
    }

    /**
     * 开启关联确认和 mandatory 返回，消息发送成功不能只依赖本地调用返回。
     * @param connectionFactory 消息连接工厂
     * @return 专用于判题消息的发送模板
     */
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

    /**
     * 沙箱启动检查通过后才启动受控并发消费者，消息使用手动确认。
     * @param connectionFactory 消息连接工厂
     * @param properties 已验证的 Worker 并发配置
     * @return prefetch 为 1 的消费容器工厂
     */
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
        factory.setContainerCustomizer(container -> container.setShutdownTimeout(shutdownTimeout(properties)));
        return factory;
    }

    /**
     * 停机最多等待既有任务截止、文件清理及终态确认；在途消息处理完成即可提前退出
     * @param properties 已验证的 Worker 消息确认配置
     * @return 容器停机等待上限，毫秒
     */
    private long shutdownTimeout(WorkerProperties properties) {
        // 终态首次确认失败后，过期恢复最多还会再次发布一次终态
        return JudgeConstants.MAX_TASK_DURATION_MILLIS + JudgeConstants.MAX_CLOCK_SKEW_MILLIS
                + GoJudgeTaskSession.CLEANUP_TIMEOUT_MILLIS + 2 * properties.getConfirmTimeoutMillis();
    }
}
