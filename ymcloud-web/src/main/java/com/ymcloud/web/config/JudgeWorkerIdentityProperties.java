package com.ymcloud.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 独立 Worker 的只读服务身份，独立于用户 JWT 和对象存储凭据。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ymcloud.judge.worker-api")
public class JudgeWorkerIdentityProperties {

    private String token;
}
