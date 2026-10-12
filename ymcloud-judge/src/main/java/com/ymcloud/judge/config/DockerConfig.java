package com.ymcloud.judge.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 独立 Worker 的 Docker 客户端，连接参数沿用 Docker 标准环境变量。
 */
@Configuration
@Slf4j
public class DockerConfig {

    /**
     * 配置 Docker 客户端
     */
    @Bean
    public DockerClient dockerClient() {
        log.info("生成 Docker 客户端");
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                // 覆盖最长 60 秒测试点及终止宽限，避免传输层提前中断合法执行。
                .responseTimeout(Duration.ofSeconds(120))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
