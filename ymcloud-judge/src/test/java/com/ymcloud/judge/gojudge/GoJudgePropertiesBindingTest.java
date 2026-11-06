package com.ymcloud.judge.gojudge;

import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.WorkerDataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 读取真实配置，验证内存单位、环境变量及文件传输默认预算
 */
class GoJudgePropertiesBindingTest {

    /**
     * 验证实际 YAML 保留自定义配置，并使输出与文件传输默认值和 Java 配置一致
     * @throws Exception 配置资源读取失败
     */
    @Test
    void actualYamlBindsNonDefaultMemoryAndTransportBudgets() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("phase3-test", Map.of(
                "YMCLOUD_GO_JUDGE_TOKEN", "test-go-judge-token-0000000000000000000",
                "YMCLOUD_GO_JUDGE_MAX_MEMORY_KIB", "393216",
                "YMCLOUD_GO_JUDGE_REQUEST_TIMEOUT", "95s",
                "YMCLOUD_GO_JUDGE_CONCURRENCY", "2",
                "YMCLOUD_WORKER_CONCURRENCY", "5",
                "YMCLOUD_GO_JUDGE_SERVICE_MEMORY_BYTES", "3221225472",
                "YMCLOUD_GO_JUDGE_ALLOW_PRIVATE_HTTP", "true",
                "YMCLOUD_WORKER_ALLOW_PRIVATE_HTTP", "true")));
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);

        GoJudgeProperties properties = Binder.get(environment)
                .bind("ymcloud.worker.go-judge", Bindable.of(GoJudgeProperties.class))
                .orElseThrow(() -> new AssertionError("go-judge 配置没有绑定"));

        properties.validate();
        assertEquals(393216, properties.getMaxProblemMemoryKiB());
        assertEquals(2, properties.getSandboxConcurrency());
        assertEquals(Duration.ofSeconds(95), properties.getRequestTimeout());
        assertEquals(536870912L, properties.getCompileMemoryBytes());
        assertEquals(64L * 1024 * 1024, properties.getMaxFileBytes());
        assertEquals(65 * 1024 * 1024, properties.getMaxRequestBytes());
        assertEquals(8 * 1024 * 1024, properties.getMaxResponseBytes());
        WorkerProperties worker = Binder.get(environment)
                .bind("ymcloud.worker", Bindable.of(WorkerProperties.class))
                .orElseThrow(() -> new AssertionError("Worker 配置没有绑定"));
        worker.validate();
        assertEquals(5, worker.getConcurrency());
        assertEquals(32 * 1024 * 1024, worker.getMaxOutputBytes());
        assertEquals(new WorkerProperties().getMaxOutputBytes(), worker.getMaxOutputBytes());
        assertEquals(new GoJudgeProperties().getMaxFileBytes(), properties.getMaxFileBytes());
        assertEquals(new GoJudgeProperties().getMaxRequestBytes(), properties.getMaxRequestBytes());
        assertTrue(properties.isAllowPrivateNetworkHttp());
        WorkerDataProperties data = Binder.get(environment)
                .bind("ymcloud.worker.data", Bindable.of(WorkerDataProperties.class))
                .orElseThrow(() -> new AssertionError("Worker 数据配置没有绑定"));
        assertTrue(data.isAllowPrivateNetworkHttp());
    }

    /**
     * Worker 按自身可用 CPU 解析默认任务数，远程沙箱保留自动并发标记
     * @throws Exception 配置资源读取失败
     */
    @Test
    void actualYamlUsesAutomaticConcurrencyDefaults() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        GoJudgeProperties sandbox = Binder.get(environment)
                .bind("ymcloud.worker.go-judge", Bindable.of(GoJudgeProperties.class)).get();
        WorkerProperties worker = Binder.get(environment)
                .bind("ymcloud.worker", Bindable.of(WorkerProperties.class)).get();
        sandbox.setToken("test-go-judge-token-0000000000000000000");

        sandbox.validate();
        worker.validate();

        assertEquals(0, sandbox.getSandboxConcurrency());
        assertEquals(Runtime.getRuntime().availableProcessors() + 1, worker.getConcurrency());
    }
}
