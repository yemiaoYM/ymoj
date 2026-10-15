package com.ymcloud.judge.gojudge;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * go-judge 的服务身份、传输边界和部署容量。令牌不参与 toString。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ymcloud.worker.go-judge")
public class GoJudgeProperties {

    private String baseUrl = "http://127.0.0.1:5050";
    private boolean allowPrivateNetworkHttp;
    private String token;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(90);
    private int maxResponseBytes = 8 * 1024 * 1024;
    private int maxRequestBytes = 24 * 1024 * 1024;
    private long maxFileBytes = 16 * 1024 * 1024;
    private long compileMemoryBytes = 512 * 1024 * 1024;
    private long compileWallTimeMillis = 10000;
    private long maxProblemMemoryKiB = 512 * 1024;
    private int processLimit = 64;
    private int sandboxConcurrency = 1;
    private long serviceMemoryBytes = 1024 * 1024 * 1024;
    private int cpuRateLimit = 1000;

    @PostConstruct
    public void validate() {
        if (token == null || token.length() < 32 || token.length() > 512
                || token.chars().anyMatch(character -> character <= 32 || character >= 127)) {
            throw new IllegalArgumentException("go-judge 独立令牌必须为 32 至 512 个可打印字符");
        }
        if (!validTimeout(connectTimeout, 60) || !validTimeout(requestTimeout, 600)
                || requestTimeout.compareTo(connectTimeout) < 0) {
            throw new IllegalArgumentException("go-judge 连接或传输超时配置无效");
        }
        if (maxFileBytes < 1024 || maxFileBytes > 256L * 1024 * 1024
                || maxResponseBytes < 1024 || maxResponseBytes > 64 * 1024 * 1024
                || maxRequestBytes < 1024 || maxRequestBytes > 256 * 1024 * 1024) {
            throw new IllegalArgumentException("go-judge 文件或 HTTP 消息大小配置无效");
        }
        if (sandboxConcurrency < 1 || sandboxConcurrency > 32 || processLimit < 4 || processLimit > 1024
                || cpuRateLimit < 1 || cpuRateLimit > 64000 || compileWallTimeMillis < 100
                || compileWallTimeMillis > 300000 || compileMemoryBytes < 32L * 1024 * 1024
                || compileMemoryBytes > 64L * 1024 * 1024 * 1024
                || maxProblemMemoryKiB < 1024 || maxProblemMemoryKiB > 64L * 1024 * 1024) {
            throw new IllegalArgumentException("go-judge 沙箱容量配置无效");
        }
        long largestSandbox = Math.max(compileMemoryBytes, maxProblemMemoryKiB * 1024);
        long reserved = 256L * 1024 * 1024 + sandboxConcurrency * 64L * 1024 * 1024;
        if (serviceMemoryBytes < sandboxConcurrency * largestSandbox + reserved
                || requestTimeout.toMillis() <= compileWallTimeMillis) {
            throw new IllegalArgumentException("go-judge 总内存、并发及编译传输预算必须配套调整");
        }
    }

    private static boolean validTimeout(Duration value, int maxSeconds) {
        return value != null && value.toMillis() >= 1 && value.compareTo(Duration.ofSeconds(maxSeconds)) <= 0;
    }
}
