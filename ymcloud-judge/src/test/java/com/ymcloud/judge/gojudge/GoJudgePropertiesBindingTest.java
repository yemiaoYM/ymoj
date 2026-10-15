package com.ymcloud.judge.gojudge;

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
 * 读取真实 application.yml，验证带 KiB 缩写的属性和部署环境变量确实生效。
 */
class GoJudgePropertiesBindingTest {

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
                "YMCLOUD_GO_JUDGE_ALLOW_PRIVATE_HTTP", "true",
                "YMCLOUD_WORKER_ALLOW_PRIVATE_HTTP", "true")));
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);

        GoJudgeProperties properties = Binder.get(environment)
                .bind("ymcloud.worker.go-judge", Bindable.of(GoJudgeProperties.class))
                .orElseThrow(() -> new AssertionError("go-judge 配置没有绑定"));

        properties.validate();
        assertEquals(393216, properties.getMaxProblemMemoryKiB());
        assertEquals(Duration.ofSeconds(95), properties.getRequestTimeout());
        assertEquals(536870912L, properties.getCompileMemoryBytes());
        assertTrue(properties.isAllowPrivateNetworkHttp());
        WorkerDataProperties data = Binder.get(environment)
                .bind("ymcloud.worker.data", Bindable.of(WorkerDataProperties.class))
                .orElseThrow(() -> new AssertionError("Worker 数据配置没有绑定"));
        assertTrue(data.isAllowPrivateNetworkHttp());
    }
}
