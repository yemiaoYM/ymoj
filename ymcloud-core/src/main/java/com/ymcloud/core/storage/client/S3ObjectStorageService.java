package com.ymcloud.core.storage.client;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.config.StorageProperties;
import org.springframework.util.DigestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: AWS S3 SDK v2 驱动，支持 SeaweedFS、MinIO 等 S3 兼容服务
 */
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client client;

    /**
     * 根据已校验配置创建独立的对象存储客户端
     * @param profile 含端点、凭据、区域和超时的客户端配置
     */
    public S3ObjectStorageService(StorageProperties.Profile profile) {
        this(S3Client.builder()
                .endpointOverride(URI.create(profile.getEndpoint()))
                .region(Region.of(profile.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(profile.getAccessKey(), profile.getSecretKey())))
                .forcePathStyle(profile.isPathStyle())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(profile.getTimeout())
                        .socketTimeout(profile.getTimeout()))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(profile.getTimeout().multipliedBy(3))
                        .apiCallAttemptTimeout(profile.getTimeout()))
                .build());
    }

    /**
     * 接管指定 S3 客户端及其关闭责任
     * @param client 已构建的 S3 客户端
     */
    public S3ObjectStorageService(S3Client client) {
        this.client = client;
    }

    /**
     * 上传 S3 对象，由调用方提供对象位置及文件摘要
     * @param bucket 由上层确定的存储桶
     * @param key 桶内对象键
     * @param source 待上传的本地文件
     * @param contentType 文件媒体类型
     * @param sha256 文件内容的 SHA-256 十六进制摘要
     */
    @Override
    public void put(String bucket, String key, Path source, String contentType, String sha256) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentMD5(contentMd5(source))
                        .metadata(Map.of("sha256", sha256))
                        .build(),
                RequestBody.fromFile(source));
    }

    /**
     * 流式计算 S3 Content-MD5 传输校验值，不作为文件身份摘要
     * @param source 待上传的本地文件
     * @return Base64 编码的 MD5 摘要
     * @throws FileException 文件无法读取
     */
    private String contentMd5(Path source) {
        try (InputStream input = Files.newInputStream(source)) {
            // MD5 用于 S3 传输校验，文件身份及版本校验仍使用 SHA-256
            return Base64.getEncoder().encodeToString(DigestUtils.md5Digest(input));
        } catch (IOException e) {
            throw new FileException("计算上传传输校验值失败", e);
        }
    }

    /**
     * 打开 S3 对象的内容流
     * @param bucket 由上层确定的存储桶
     * @param key 桶内对象键
     * @return 内容流，由调用方关闭
     */
    @Override
    public InputStream open(String bucket, String key) {
        return client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    /**
     * 删除指定 S3 对象
     * @param bucket 由上层确定的存储桶
     * @param key 桶内对象键
     */
    @Override
    public void delete(String bucket, String key) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    /**
     * 关闭本驱动持有的 SDK 客户端并释放连接资源
     */
    @Override
    public void close() {
        client.close();
    }
}
