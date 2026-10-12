package com.ymcloud.judge.config;

import com.ymcloud.judge.data.TestDataCache;
import com.ymcloud.judge.listeners.ResultJournal;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 非 Web Worker 的指标可通过 Actuator JMX 导出，不开放额外 HTTP 服务。
 */
@Slf4j
@Component
public class WorkerMetrics {

    private final ResultJournal journal;

    public WorkerMetrics(TestDataCache cache, ResultJournal journal, MeterRegistry registry) {
        this.journal = journal;
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

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void pruneResults() {
        try {
            journal.prune();
        } catch (IOException e) {
            log.warn("Worker 结果日志保留期清理失败");
        }
    }
}
