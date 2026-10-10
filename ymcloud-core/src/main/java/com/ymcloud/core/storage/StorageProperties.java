package com.ymcloud.core.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按配置名管理存储，已有文件始终使用其记录中的配置。
 * 凭据字段不参与 toString，避免出现在日志中。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ymcloud.storage")
public class StorageProperties {

    private String active = "local";
    private String configEncryptionKey;
    private long maxFileSize = 50L * 1024 * 1024;
    private Map<String, Profile> profiles = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Profile {
        private String type = "local";
        private String endpoint;
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;
        private String publicBucket = "ymcloud-public";
        private String privateBucket = "ymcloud-private";
        private boolean pathStyle = true;
        private Duration timeout = Duration.ofSeconds(60);
    }
}
