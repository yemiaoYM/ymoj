package com.ymcloud.core.storage.client;

import com.ymcloud.core.storage.config.StorageClientConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 为 S3 兼容服务装配 Netty 客户端、异步传输管理器和签名器
 */
public class DefaultStorageClientImpl extends AbstractStorageClientImpl {

    /**
     * 构造默认 S3 存储客户端
     * @param clientId 客户端 ID
     * @param config S3 存储客户端配置
     */
    public DefaultStorageClientImpl(String clientId, StorageClientConfig config) {
        super(clientId, config);
        initialize();
    }

    /**
     * 初始化默认 S3 客户端、传输管理器和预签名生成器
     */
    @Override
    protected void doInitialize() {
        Region region = Region.of(config.getRegion());
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
        var addressing = S3Configuration.builder().pathStyleAccessEnabled(config.isPathStyle()).build();
        s3AsyncClient = S3AsyncClient.builder()
                .endpointOverride(config.getEndpoint())
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(addressing)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(100)
                        .maxPendingConnectionAcquires(1000)
                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))
                        .connectionTimeout(Duration.ofSeconds(60))
                        .readTimeout(Duration.ofSeconds(60))
                        .writeTimeout(Duration.ofSeconds(60)))
                .overrideConfiguration(builder -> builder.apiCallAttemptTimeout(Duration.ofSeconds(60))
                        .apiCallTimeout(Duration.ofMinutes(3)))
                .build();
        s3TransferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build();
        s3Presigner = S3Presigner.builder()
                .endpointOverride(config.getEndpoint())
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(addressing)
                .build();
        asyncExecutor = Executors.newFixedThreadPool(config.getAsyncExecutorConfig().getCorePoolSize());
    }
}
