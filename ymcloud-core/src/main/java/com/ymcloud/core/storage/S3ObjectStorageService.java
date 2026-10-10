package com.ymcloud.core.storage;

import com.ymcloud.common.exception.file.FileException;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * AWS S3 SDK v2 驱动，支持 SeaweedFS、MinIO 等 S3 兼容服务。
 */
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client client;

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

    public S3ObjectStorageService(S3Client client) {
        this.client = client;
    }

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

    private String contentMd5(Path source) {
        try (InputStream input = Files.newInputStream(source)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
            // MD5 用于 S3 传输校验，文件身份及版本校验仍使用 SHA-256。
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new FileException("计算上传传输校验值失败", e);
        }
    }

    @Override
    public InputStream open(String bucket, String key) {
        return client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    @Override
    public void delete(String bucket, String key) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    @Override
    public void close() {
        client.close();
    }
}
