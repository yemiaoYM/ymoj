package com.ymcloud.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerHealthProbe;
import com.ymcloud.judge.config.WorkerMetrics;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.gojudge.GoJudgeClient;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeProperties;
import com.ymcloud.judge.gojudge.GoJudgeStartupCheck;
import com.ymcloud.judge.listeners.JudgeListener;
import com.ymcloud.judge.listeners.ResultJournal;
import com.ymcloud.judge.listeners.ResultPublisher;
import com.ymcloud.judge.listeners.WorkerExecutionStore;
import com.ymcloud.judge.listeners.WorkerTaskValidator;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-25
 * @Description: 通过真实 Spring 注入处理器验证判题组件依赖与配置，不连接外部服务。
 */
class WorkerComponentWiringTest {

    /**
     * 注入每个显式依赖和配置值，发送路由不能回退成错误的默认值。
     */
    @Test
    void springInjectsRequiredDependenciesAndConfiguredRoutes() {
        try (var context = new AnnotationConfigApplicationContext()) {
            GoJudgeClient goJudgeClient = mock(GoJudgeClient.class);
            GoJudgeProperties goJudgeProperties = mock(GoJudgeProperties.class);
            WorkerProperties workerProperties = mock(WorkerProperties.class);
            GoJudgeExecutor goJudgeExecutor = mock(GoJudgeExecutor.class);
            TestDataCache testDataCache = mock(TestDataCache.class);
            ObjectMapper objectMapper = mock(ObjectMapper.class);
            WorkerTaskValidator workerTaskValidator = mock(WorkerTaskValidator.class);
            WorkerExecutionStore workerExecutionStore = mock(WorkerExecutionStore.class);
            ResultJournal resultJournal = mock(ResultJournal.class);
            ResultPublisher resultPublisher = mock(ResultPublisher.class);
            JudgeRun judgeRun = mock(JudgeRun.class);
            MeterRegistry meterRegistry = mock(MeterRegistry.class);
            RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
            context.getBeanFactory().registerSingleton("goJudgeClient", goJudgeClient);
            context.getBeanFactory().registerSingleton("goJudgeProperties", goJudgeProperties);
            context.getBeanFactory().registerSingleton("workerProperties", workerProperties);
            context.getBeanFactory().registerSingleton("goJudgeExecutor", goJudgeExecutor);
            context.getBeanFactory().registerSingleton("testDataCache", testDataCache);
            context.getBeanFactory().registerSingleton("objectMapper", objectMapper);
            context.getBeanFactory().registerSingleton("workerTaskValidator", workerTaskValidator);
            context.getBeanFactory().registerSingleton("workerExecutionStore", workerExecutionStore);
            context.getBeanFactory().registerSingleton("resultJournal", resultJournal);
            context.getBeanFactory().registerSingleton("resultPublisher", resultPublisher);
            context.getBeanFactory().registerSingleton("judgeRun", judgeRun);
            context.getBeanFactory().registerSingleton("meterRegistry", meterRegistry);
            context.getBeanFactory().registerSingleton("rabbitTemplate", rabbitTemplate);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("wiring", Map.of(
                    "ymcloud.judge.exchange", "wiring.exchange",
                    "ymcloud.judge.result-routing-key", "wiring.route")));
            context.refresh();

            GoJudgeExecutor goJudgeExecutorComponent = new GoJudgeExecutor();
            context.getAutowireCapableBeanFactory().autowireBean(goJudgeExecutorComponent);
            assertSame(goJudgeClient, ReflectionTestUtils.getField(goJudgeExecutorComponent, "client"));
            assertSame(goJudgeProperties, ReflectionTestUtils.getField(goJudgeExecutorComponent, "properties"));
            assertSame(workerProperties, ReflectionTestUtils.getField(goJudgeExecutorComponent, "worker"));

            GoJudgeStartupCheck goJudgeStartupCheckComponent = new GoJudgeStartupCheck();
            context.getAutowireCapableBeanFactory().autowireBean(goJudgeStartupCheckComponent);
            assertSame(goJudgeClient, ReflectionTestUtils.getField(goJudgeStartupCheckComponent, "client"));
            assertSame(goJudgeProperties, ReflectionTestUtils.getField(goJudgeStartupCheckComponent, "properties"));
            assertSame(workerProperties, ReflectionTestUtils.getField(goJudgeStartupCheckComponent, "workerProperties"));

            JudgeRun judgeRunComponent = new JudgeRun();
            context.getAutowireCapableBeanFactory().autowireBean(judgeRunComponent);
            assertSame(goJudgeExecutor, ReflectionTestUtils.getField(judgeRunComponent, "executor"));
            assertSame(testDataCache, ReflectionTestUtils.getField(judgeRunComponent, "cache"));
            assertSame(workerProperties, ReflectionTestUtils.getField(judgeRunComponent, "properties"));

            JudgeListener judgeListenerComponent = new JudgeListener();
            context.getAutowireCapableBeanFactory().autowireBean(judgeListenerComponent);
            assertSame(objectMapper, ReflectionTestUtils.getField(judgeListenerComponent, "mapper"));
            assertSame(workerTaskValidator, ReflectionTestUtils.getField(judgeListenerComponent, "validator"));
            assertSame(workerExecutionStore, ReflectionTestUtils.getField(judgeListenerComponent, "store"));
            assertSame(resultJournal, ReflectionTestUtils.getField(judgeListenerComponent, "journal"));
            assertSame(resultPublisher, ReflectionTestUtils.getField(judgeListenerComponent, "publisher"));
            assertSame(judgeRun, ReflectionTestUtils.getField(judgeListenerComponent, "runner"));
            assertSame(workerProperties, ReflectionTestUtils.getField(judgeListenerComponent, "properties"));
            assertSame(meterRegistry, ReflectionTestUtils.getField(judgeListenerComponent, "metrics"));

            ResultPublisher resultPublisherComponent = new ResultPublisher();
            context.getAutowireCapableBeanFactory().autowireBean(resultPublisherComponent);
            assertSame(rabbitTemplate, ReflectionTestUtils.getField(resultPublisherComponent, "rabbit"));
            assertSame(objectMapper, ReflectionTestUtils.getField(resultPublisherComponent, "mapper"));
            assertSame(workerProperties, ReflectionTestUtils.getField(resultPublisherComponent, "properties"));
            assertEquals("wiring.exchange", ReflectionTestUtils.getField(resultPublisherComponent, "exchange"));
            assertEquals("wiring.route", ReflectionTestUtils.getField(resultPublisherComponent, "resultRoute"));
        }
    }

    /**
     * 必需依赖缺失时装配失败，不能延迟到消息处理时空指针。
     */
    @Test
    void missingRequiredDependencyFailsInjection() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            assertThrows(UnsatisfiedDependencyException.class,
                    () -> context.getAutowireCapableBeanFactory().autowireBean(new GoJudgeExecutor()));
        }
    }

    /**
     * 真实生命周期在注入后注册指标和初始化目录，关闭上下文时移除健康标记。
     * @param directory 本测试独占的临时目录
     * @throws Exception 临时文件操作失败
     */
    @Test
    void lifecycleInitializesMetricsAndHealthAfterInjection(@TempDir Path directory) throws Exception {
        Path health = directory.resolve("health");
        Files.writeString(health, "stale");
        var metrics = new SimpleMeterRegistry();
        try (var context = new AnnotationConfigApplicationContext()) {
            WorkerProperties properties = new WorkerProperties();
            properties.setConcurrency(1);
            properties.setWorkDir(directory);
            TestDataCache cache = mock(TestDataCache.class);
            when(cache.statistics()).thenReturn(new TestDataCache.Statistics(7, 3, 0, 0, 1024, 0, 1));
            RabbitListenerEndpointRegistry registry = mock(RabbitListenerEndpointRegistry.class);
            SimpleMessageListenerContainer container = mock(SimpleMessageListenerContainer.class);
            when(registry.getListenerContainers()).thenReturn(List.of(container));
            when(container.isRunning()).thenReturn(true);
            when(container.getQueueNames()).thenReturn(new String[]{JudgeProtocol.TASK_QUEUE});
            when(container.getActiveConsumerCount()).thenReturn(1);
            context.getBeanFactory().registerSingleton("workerProperties", properties);
            context.getBeanFactory().registerSingleton("testDataCache", cache);
            context.getBeanFactory().registerSingleton("resultJournal", mock(ResultJournal.class));
            context.getBeanFactory().registerSingleton("metrics", metrics);
            context.getBeanFactory().registerSingleton("registry", registry);
            context.register(WorkerHealthProbe.class, WorkerMetrics.class);
            context.refresh();

            assertFalse(Files.exists(health));
            assertEquals(7, metrics.get("ymcloud.judge.cache.hits").functionCounter().count());
            assertEquals(1024, metrics.get("ymcloud.judge.cache.bytes").gauge().value());
            context.getBean(WorkerHealthProbe.class).applicationReady();
            assertTrue(Files.exists(health));
        } finally {
            metrics.close();
        }
        assertFalse(Files.exists(health));
    }
}
