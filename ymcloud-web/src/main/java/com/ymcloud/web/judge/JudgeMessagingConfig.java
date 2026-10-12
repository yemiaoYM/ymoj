package com.ymcloud.web.judge;

import com.ymcloud.pojo.contract.judge.JudgeProtocol;
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

/** Web 与 Worker 使用相同的版本化拓扑，发布不再依赖 RabbitMQ 延迟交换机插件。 */
@Slf4j
@Configuration
public class JudgeMessagingConfig {

    @Bean("judgeWorkerExchange")
    public TopicExchange exchange(@Value("${ymcloud.judge.exchange:" + JudgeProtocol.EXCHANGE + "}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean("judgeTaskQueue")
    public Queue taskQueue(@Value("${ymcloud.judge.task-queue:" + JudgeProtocol.TASK_QUEUE + "}") String name,
                           @Value("${ymcloud.judge.exchange:" + JudgeProtocol.EXCHANGE + "}") String exchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey(JudgeProtocol.DEAD_ROUTE)
                .build();
    }

    @Bean("judgeResultQueue")
    public Queue resultQueue(@Value("${ymcloud.judge.result-queue:" + JudgeProtocol.RESULT_QUEUE + "}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean("judgeTaskRetryQueue")
    public Queue retryQueue(@Value("${ymcloud.judge.task-queue:" + JudgeProtocol.TASK_QUEUE + "}") String name,
                            @Value("${ymcloud.judge.exchange:" + JudgeProtocol.EXCHANGE + "}") String exchange,
                            @Value("${ymcloud.judge.task-routing-key:" + JudgeProtocol.TASK_ROUTE + "}") String route) {
        return QueueBuilder.durable(name + ".retry")
                .ttl(3000)
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey(route)
                .build();
    }

    @Bean("judgeTaskDeadQueue")
    public Queue deadQueue(@Value("${ymcloud.judge.task-queue:" + JudgeProtocol.TASK_QUEUE + "}") String name) {
        return QueueBuilder.durable(name + ".dead").build();
    }

    @Bean("judgeTaskBinding")
    public Binding taskBinding(@Qualifier("judgeTaskQueue") Queue queue,
                                @Qualifier("judgeWorkerExchange") TopicExchange exchange,
                                @Value("${ymcloud.judge.task-routing-key:" + JudgeProtocol.TASK_ROUTE + "}")
                                String route) {
        return BindingBuilder.bind(queue).to(exchange).with(route);
    }

    @Bean("judgeResultBinding")
    public Binding resultBinding(@Qualifier("judgeResultQueue") Queue queue,
                                  @Qualifier("judgeWorkerExchange") TopicExchange exchange,
                                  @Value("${ymcloud.judge.result-routing-key:" + JudgeProtocol.RESULT_ROUTE + "}")
                                  String route) {
        return BindingBuilder.bind(queue).to(exchange).with(route);
    }

    @Bean("judgeTaskRetryBinding")
    public Binding retryBinding(@Qualifier("judgeTaskRetryQueue") Queue queue,
                                 @Qualifier("judgeWorkerExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(JudgeProtocol.RETRY_ROUTE);
    }

    @Bean("judgeTaskDeadBinding")
    public Binding deadBinding(@Qualifier("judgeTaskDeadQueue") Queue queue,
                                @Qualifier("judgeWorkerExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(JudgeProtocol.DEAD_ROUTE);
    }

    @Bean("judgeRabbitTemplate")
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new SimpleMessageConverter());
        template.setMandatory(true);
        return template;
    }

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
                    // 原始异常可能包含 SQL 参数或私有输出，框架日志只保留安全的错误说明。
                    throw new AmqpRejectAndDontRequeueException("判题结果处理失败");
                })
                .build());
        return factory;
    }
}
