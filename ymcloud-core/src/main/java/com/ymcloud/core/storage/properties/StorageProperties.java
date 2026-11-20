package com.ymcloud.core.storage.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 缓存中单个对象存储服务的配置属性
 */
@Getter
@Setter
public class StorageProperties {

    /**
     * 对象存储服务端点
     */
    private String endpoint;

    /**
     * 可选的桶访问域名
     */
    private String domain;

    /**
     * 默认对象目录前缀
     */
    private String prefix;

    /**
     * SDK 签名使用的访问标识
     */
    private String accessKey;

    /**
     * SDK 签名密钥
     */
    private String secretKey;

    /**
     * 未显式指定桶时使用的桶名
     */
    private String bucketName;

    /**
     * 签名区域，缺省时使用 us-east-1
     */
    private String region;

    /**
     * 协议标识，Y 使用 HTTPS，N 使用 HTTP
     */
    private String isHttps;

    /**
     * 访问策略编码，与 AccessPolicy 对应
     */
    private String accessPolicy;
}
