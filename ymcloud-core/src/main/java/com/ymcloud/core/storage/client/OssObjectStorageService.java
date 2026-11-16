package com.ymcloud.core.storage.client;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.config.StorageProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 阿里云原生 OSS 驱动，使用官方 SDK 的 V4 签名
 */
public class OssObjectStorageService implements ObjectStorageService {

    private final OSS client;

    /**
     * 根据已校验配置创建独立的对象存储客户端
     * @param profile 含端点、凭据、区域和超时的客户端配置
     */
    public OssObjectStorageService(StorageProperties.Profile profile) {
        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setSignatureVersion(SignVersion.V4);
        config.setConnectionTimeout(Math.toIntExact(profile.getTimeout().toMillis()));
        config.setSocketTimeout(Math.toIntExact(profile.getTimeout().toMillis()));
        client = OSSClientBuilder.create()
                .endpoint(profile.getEndpoint())
                .region(profile.getRegion())
                .credentialsProvider(new DefaultCredentialProvider(profile.getAccessKey(), profile.getSecretKey()))
                .clientConfiguration(config)
                .build();
    }

    /**
     * 接管指定 OSS 客户端及其关闭责任
     * @param client 已构建的 OSS 客户端
     */
    public OssObjectStorageService(OSS client) {
        this.client = client;
    }

    /**
     * 上传 OSS 对象，由调用方提供对象位置及文件摘要
     * @param bucket 由上层确定的存储桶
     * @param key 桶内对象键
     * @param source 待上传的本地文件
     * @param contentType 文件媒体类型
     * @param sha256 文件内容的 SHA-256 十六进制摘要
     */
    @Override
    public void put(String bucket, String key, Path source, String contentType, String sha256) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setUserMetadata(Map.of("sha256", sha256));
        try (InputStream input = Files.newInputStream(source)) {
            metadata.setContentLength(Files.size(source));
            client.putObject(new PutObjectRequest(bucket, key, input, metadata));
        } catch (IOException e) {
            throw new FileException("读取待上传文件失败", e);
        }
    }

    /**
     * 打开 OSS 对象的内容流
     * @param bucket 由上层确定的存储桶
     * @param key 桶内对象键
     * @return 内容流，由调用方关闭
     */
    @Override
    public InputStream open(String bucket, String key) {
        return client.getObject(bucket, key).getObjectContent();
    }

    /**
     * 删除指定 OSS 对象
     * @param bucket 由上层确定的存储桶
     * @param key 桶内对象键
     */
    @Override
    public void delete(String bucket, String key) {
        client.deleteObject(bucket, key);
    }

    /**
     * 关闭本驱动持有的 SDK 客户端并释放连接资源
     */
    @Override
    public void close() {
        client.shutdown();
    }
}
