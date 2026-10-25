package com.ymcloud.judge.config;

import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.listeners.ResultJournal;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 非 Web Worker 的指标可通过 Actuator JMX 导出，不开放额外 HTTP 服务。
 */
@Slf4j
@Component
public class WorkerMetrics {

    @Autowired
    private ResultJournal journal;

    @Autowired
    private TestDataCache cache;

    @Autowired
    private MeterRegistry registry;

    /**
     * 依赖注入后注册缓存命中、校验和容量指标。
     */
    @PostConstruct
    public void initialize() {
        FunctionCounter.builder("ymcloud.judge.cache.hits", cache,
                value -> value.statistics().hits()).register(registry);
        FunctionCounter.builder("ymcloud.judge.cache.misses", cache,
                value -> value.statistics().misses()).register(registry);
        FunctionCounter.builder("ymcloud.judge.cache.download.failures", cache,
                value -> value.statistics().downloadFailures()).register(registry);
        FunctionCounter.builder("ymcloud.judge.cache.verification.failures", cache,
                value -> value.statistics().verificationFailures()).register(registry);
        Gauge.builder("ymcloud.judge.cache.bytes", cache, value -> value.statistics().readyBytes()).register(registry);
        Gauge.builder("ymcloud.judge.cache.versions", cache, value -> value.statistics().versions()).register(registry);
    }

    /**
     * 定期清理过期终态记录，失败留待下次重试。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void pruneResults() {
        try {
            journal.prune();
        } catch (IOException e) {
            log.warn("Worker 结果日志保留期清理失败，异常类型：{}", e.getClass().getSimpleName());
        }
    }
}
