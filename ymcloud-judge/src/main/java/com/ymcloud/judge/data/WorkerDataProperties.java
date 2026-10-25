package com.ymcloud.judge.data;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: Web 数据接口的服务身份与超时。密钥不参与 toString，也不复用用户 JWT。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ymcloud.worker.data")
public class WorkerDataProperties {

    private String baseUrl = "http://127.0.0.1:9091";
    private boolean allowPrivateNetworkHttp;
    private String token;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
}
