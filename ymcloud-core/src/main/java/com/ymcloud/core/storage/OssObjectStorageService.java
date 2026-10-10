package com.ymcloud.core.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.ymcloud.common.exception.file.FileException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 阿里云原生 OSS 驱动，使用官方 SDK 的 V4 签名。
 */
public class OssObjectStorageService implements ObjectStorageService {

    private final OSS client;

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

    public OssObjectStorageService(OSS client) {
        this.client = client;
    }

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

    @Override
    public InputStream open(String bucket, String key) {
        return client.getObject(bucket, key).getObjectContent();
    }

    @Override
    public void delete(String bucket, String key) {
        client.deleteObject(bucket, key);
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
