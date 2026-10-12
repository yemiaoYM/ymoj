package com.ymcloud.judge.data;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 每个 Worker 使用独立的私有缓存目录，容量包含尚未完成的下载。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ymcloud.worker.cache")
public class TestDataCacheProperties {

    private String directory = "./files/worker/cache";
    private long maxBytes = 5L * 1024 * 1024 * 1024;
    private long maxVersionBytes = 1024L * 1024 * 1024;
    private long maxFileBytes = 50L * 1024 * 1024;
    private int maxManifestBytes = 4 * 1024 * 1024;
    private int maxCases = 10_000;
}
