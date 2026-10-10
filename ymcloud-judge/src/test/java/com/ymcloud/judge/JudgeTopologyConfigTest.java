package com.ymcloud.judge;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.common.utils.MessageQueueUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.judge.bo.JudgeResult;
import com.ymcloud.judge.listeners.JudgeListener;
import com.ymcloud.judge.service.impl.JudgeServiceImpl;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.bo.JudgeTask;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 实际 Spring 配置注入与监听绑定使用同一拓扑，默认保持旧队列兼容。
 */
class JudgeTopologyConfigTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void producerAndConsumerResolveTheSameConfiguredTopology(boolean isolatedQueue) throws Exception {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ymcloud.docker.host-working-dir", "target/judge-test");
        properties.put("ymcloud.docker.container-working-dir", "/app");
        if (isolatedQueue) {
            properties.put("ymcloud.judge.exchange", "judge.storage-v1.topic");
            properties.put("ymcloud.judge.task-queue", "judge.storage-v1.queue");
            properties.put("ymcloud.judge.result-queue", "judge.storage-v1.result.queue");
            properties.put("ymcloud.judge.task-routing-key", "storage-v1.judge");
            properties.put("ymcloud.judge.result-routing-key", "storage-v1.judge.result");
        }
        String exchange = isolatedQueue ? "judge.storage-v1.topic" : "judge.topic";
        String taskQueue = isolatedQueue ? "judge.storage-v1.queue" : "judge.queue";
        String resultQueue = isolatedQueue ? "judge.storage-v1.result.queue" : "judge.result.queue";
        String taskRoutingKey = isolatedQueue ? "storage-v1.judge" : "judge";
        String resultRoutingKey = isolatedQueue ? "storage-v1.judge.result" : "judge.result";

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("judge-test", properties));
            context.registerBean(PropertySourcesPlaceholderConfigurer.class);
            registerMock(context, MessageQueueUtils.class);
            registerMock(context, RedisCache.class);
            registerMock(context, JudgeEntityService.class);
            registerMock(context, ProblemEntityService.class);
            registerMock(context, LanguageEntityService.class);
            registerMock(context, ContestEntityService.class);
            registerMock(context, ContestProblemEntityService.class);
            registerMock(context, ContestUserEntityService.class);
            registerMock(context, TestDataService.class);
            registerMock(context, JudgeMapper.class);
            registerMock(context, DockerClient.class);
            context.registerBean(JudgeServiceImpl.class);
            context.registerBean(JudgeRun.class);
            context.refresh();

            JudgeServiceImpl service = context.getBean(JudgeServiceImpl.class);
            JudgeRun judgeRun = context.getBean(JudgeRun.class);
            assertEquals(exchange, ReflectionTestUtils.getField(service, "judgeExchange"));
            assertEquals(taskRoutingKey, ReflectionTestUtils.getField(service, "judgeTaskRoutingKey"));
            assertEquals(exchange, ReflectionTestUtils.getField(judgeRun, "judgeExchange"));
            assertEquals(resultRoutingKey, ReflectionTestUtils.getField(judgeRun, "judgeResultRoutingKey"));

            QueueBinding task = JudgeListener.class.getDeclaredMethod("listenJudge", JudgeTask.class)
                    .getAnnotation(RabbitListener.class).bindings()[0];
            QueueBinding result = JudgeListener.class.getDeclaredMethod("listenJudgeResultUpdate", JudgeResult.class)
                    .getAnnotation(RabbitListener.class).bindings()[0];
            assertEquals(taskQueue, context.getEnvironment().resolveRequiredPlaceholders(task.value().name()));
            assertEquals(resultQueue, context.getEnvironment().resolveRequiredPlaceholders(result.value().name()));
            assertEquals(exchange, context.getEnvironment().resolveRequiredPlaceholders(task.exchange().value()));
            assertEquals(exchange, context.getEnvironment().resolveRequiredPlaceholders(result.exchange().value()));
            assertEquals(taskRoutingKey, context.getEnvironment().resolveRequiredPlaceholders(task.key()[0]));
            assertEquals(resultRoutingKey, context.getEnvironment().resolveRequiredPlaceholders(result.key()[0]));
        }
    }

    private <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> type) {
        context.getBeanFactory().registerSingleton(type.getSimpleName(), mock(type));
    }
}
