package com.ymcloud.judge.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Worker 的本地目录、执行边界和消息恢复配置。
 */
@Data
@ConfigurationProperties(prefix = "ymcloud.worker")
public class WorkerProperties {

    private String id = "worker-" + UUID.randomUUID();
    private Path workDir = Path.of("./files/worker/work");
    private Path journalDir = Path.of("./files/worker/results");
    private String redisPrefix = "ymcloud:judge-worker-v2:";
    private int concurrency = 1;
    private int maxAttempts = 3;
    private long leaseMillis = 30000;
    private long confirmTimeoutMillis = 10000;
    private long retentionMillis = 7 * 24 * 60 * 60 * 1000L;
    private int maxOutputBytes = 1024 * 1024;

    @PostConstruct
    public void validate() {
        if (id == null || id.isBlank() || id.length() > 128 || workDir == null || journalDir == null
                || redisPrefix == null || !redisPrefix.matches("[A-Za-z0-9:_-]{1,128}")) {
            throw new IllegalArgumentException("Worker 标识、目录或 Redis 前缀配置无效");
        }
        if (concurrency < 1 || concurrency > 32 || maxAttempts < 1 || maxAttempts > 10
                || leaseMillis < 3000 || leaseMillis > 300000 || confirmTimeoutMillis < 1000
                || confirmTimeoutMillis > 60000 || leaseMillis <= 2 * confirmTimeoutMillis
                || retentionMillis < 60 * 60 * 1000L
                || retentionMillis > 30 * 24 * 60 * 60 * 1000L) {
            throw new IllegalArgumentException("Worker 并发、租约或恢复配置无效");
        }
        if (maxOutputBytes < 1024 || maxOutputBytes > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("Worker 输出限制配置无效");
        }
    }
}
