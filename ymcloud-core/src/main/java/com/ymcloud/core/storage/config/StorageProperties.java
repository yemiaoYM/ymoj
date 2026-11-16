package com.ymcloud.core.storage.config;

import com.ymcloud.pojo.enums.StorageDriverType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 绑定命名存储配置及上传限制，已有文件使用记录中的配置
 * 凭据字段不参与 toString，避免出现在日志中
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ymcloud.storage")
public class StorageProperties {

    /**
     * 静态默认存储标识，已有文件仍按各自记录选择配置
     */
    private String active = "local";

    /**
     * 显式主密钥，必须为 Base64 编码的 32 字节值，未设置时使用本地密钥库
     */
    private String configEncryptionKey;

    /**
     * 单文件上传上限，单位字节，默认 50 MiB
     */
    private long maxFileSize = 50L * 1024 * 1024;

    /**
     * 按标识索引的静态客户端配置
     */
    private Map<String, Profile> profiles = new LinkedHashMap<>();

    /**
     * 单个存储客户端的连接参数，凭据仅供内部使用
     */
    @Getter
    @Setter
    public static class Profile {

        /**
         * 必须显式指定的访问协议：LOCAL、S3 或 ALIYUN_OSS
         */
        private StorageDriverType driverType;

        /**
         * 服务商描述，不参与客户端选择
         */
        private String provider;

        /**
         * 对象存储服务端点，本地驱动不使用
         */
        private String endpoint;

        /**
         * 签名使用的服务区域
         */
        private String region = "us-east-1";

        /**
         * 客户端访问标识，仅供内部使用
         */
        private String accessKey;

        /**
         * 客户端访问密钥，不得写入日志或对外响应
         */
        private String secretKey;

        /**
         * 公开对象存储桶，与私有桶分离
         */
        private String publicBucket = "ymcloud-public";

        /**
         * 私有对象存储桶，与公开桶分离
         */
        private String privateBucket = "ymcloud-private";

        /**
         * S3 是否使用路径式桶寻址
         */
        private boolean pathStyle = true;

        /**
         * 客户端连接及读写超时，远端驱动要求为正时长
         */
        private Duration timeout = Duration.ofSeconds(60);
    }
}
