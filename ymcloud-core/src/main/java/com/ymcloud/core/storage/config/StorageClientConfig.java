package com.ymcloud.core.storage.config;

import com.ymcloud.core.storage.constant.StorageConstant;
import com.ymcloud.core.storage.enums.AccessPolicy;
import com.ymcloud.core.storage.exception.S3StorageException;
import com.ymcloud.core.storage.properties.StorageProperties;
import com.ymcloud.core.storage.util.BucketUrlUtil;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.net.URI;
import java.util.Locale;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 用于客户端创建、配置比对和签名的不可变配置
 */
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
public final class StorageClientConfig
        implements Config<StorageClientConfig, StorageClientConfig.StorageClientConfigBuilder> {

    private final URI endpoint;
    private final URI domain;
    private final boolean pathStyle;
    private final String bucket;
    private final String prefix;
    private final String accessKey;
    private final String secretKey;

    @Builder.Default
    private final String region = "us-east-1";

    @Builder.Default
    private final AccessControlPolicyConfig accessControlPolicyConfig = AccessControlPolicyConfig.DEFAULT;

    @Builder.Default
    private final StorageAsyncExecutorConfig asyncExecutorConfig = StorageAsyncExecutorConfig.DEFAULT;

    /**
     * 把配置缓存中的属性转换为实际客户端参数
     * @param properties 单个服务的存储属性
     * @return 固定了端点、访问策略和签名区域的配置
     */
    public static StorageClientConfig fromProperties(StorageProperties properties) {
        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new S3StorageException("存储端点未配置");
        }
        String host = endpoint.toLowerCase(Locale.ROOT);
        boolean https = "Y".equals(properties.getIsHttps());
        String domain = properties.getDomain();
        String region = properties.getRegion();
        String policy = properties.getAccessPolicy();
        return builder()
                .endpoint(BucketUrlUtil.endpoint(https, endpoint))
                .domain(domain == null || domain.isBlank() ? null : BucketUrlUtil.endpoint(https, domain))
                .pathStyle(StorageConstant.CLOUD_SERVICE.stream().noneMatch(host::contains))
                .bucket(properties.getBucketName())
                .prefix(properties.getPrefix())
                .accessKey(properties.getAccessKey())
                .secretKey(properties.getSecretKey())
                .region(region == null || region.isBlank() ? "us-east-1" : region)
                .accessControlPolicyConfig(policy == null || policy.isBlank() ? AccessControlPolicyConfig.DEFAULT
                        : AccessControlPolicyConfig.builder().enabled(true).accessPolicy(AccessPolicy.fromCode(policy)).build())
                .build();
    }

    /**
     * 根据目标桶选择自定义域名或存储服务端点
     * @param bucketName 访问的桶名
     * @return 桶中对象的访问基址
     */
    public String getBucketUrl(String bucketName) {
        if (bucketName.equals(bucket) && domain != null) {
            return pathStyle ? BucketUrlUtil.bucketUrl(domain, bucketName, true) : domain.toString();
        }
        return BucketUrlUtil.bucketUrl(endpoint, bucketName, pathStyle);
    }

    /**
     * 复制当前客户端配置
     * @return 与当前值相同的新配置
     */
    @Override
    public StorageClientConfig copy() {
        return toBuilder().build();
    }

    /**
     * 设置客户端参数并避免将访问密钥写入诊断消息
     */
    public static class StorageClientConfigBuilder {

        /**
         * 显示正在配置的存储桶
         * @return 不含凭据的构建器说明
         */
        @Override
        public String toString() {
            return "StorageClientConfigBuilder(bucket=" + bucket + ")";
        }
    }
}
