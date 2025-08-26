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
 * @PackageName: com.ymcloud.judge.config
 * @ClassName: DockerConfig
 * @Author: Yemiao
 * @CreateTime: 2025-08-24  22:10
 * @Description: docker 配置类
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
        //自动根据环境进行配置
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }


}
