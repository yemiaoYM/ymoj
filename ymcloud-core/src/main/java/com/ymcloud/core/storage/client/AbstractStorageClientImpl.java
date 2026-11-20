package com.ymcloud.core.storage.client;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.ymcloud.core.storage.config.StorageClientConfig;
import com.ymcloud.core.storage.exception.S3StorageException;
import com.ymcloud.core.storage.io.OutputStreamDownloadSubscriber;
import com.ymcloud.core.storage.model.GetObjectResult;
import com.ymcloud.core.storage.model.HandleAsyncResult;
import com.ymcloud.core.storage.model.Options;
import com.ymcloud.core.storage.model.PutObjectResult;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.ResponsePublisher;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignRequest;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadRequest;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 默认桶重载、异步传输、签名和客户端生命周期
 */
public abstract class AbstractStorageClientImpl implements StorageClient {

    private volatile boolean initialized;

    /**
     * 存储配置标识
     * 用于标识客户端，初始化后不允许更改
     */
    protected final String clientId;

    /**
     * S3 存储客户端配置
     */
    protected final StorageClientConfig config;

    /**
     * Amazon S3 异步客户端
     */
    protected S3AsyncClient s3AsyncClient;

    /**
     * 用于管理 S3 数据传输的高级工具
     */
    protected S3TransferManager s3TransferManager;

    /**
     * AWS S3 预签名 URL 生成器
     */
    protected S3Presigner s3Presigner;

    /**
     * 异步调度线程池
     */
    protected ExecutorService asyncExecutor;

    /**
     * 构造 S3 存储客户端基础实现
     * @param clientId 客户端 ID
     * @param config S3 存储客户端配置
     */
    protected AbstractStorageClientImpl(String clientId, StorageClientConfig config) {
        this.clientId = Objects.requireNonNull(clientId);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * 获取客户端 ID
     * @return 客户端 ID
     */
    @Override
    public String clientId() {
        return this.clientId;
    }

    /**
     * 获取客户端配置副本
     * @return 客户端配置副本
     */
    @Override
    public StorageClientConfig config() {
        return config.copy();
    }

    /**
     * 判断客户端是否已经初始化
     * @return 是否已初始化
     */
    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 初始化底层 S3 客户端资源
     */
    @Override
    public synchronized void initialize() {
        if (isInitialized()) {
            return;
        }
        try {
            doInitialize();
            initialized = true;
        } catch (Exception e) {
            close();
            throw toStorageException(e);
        }
    }

    /**
     * 执行客户端具体初始化逻辑
     */
    protected abstract void doInitialize();

    /**
     * 使用回调校验当前客户端配置
     * @param verifyConfigAction 配置校验回调
     * @return 是否校验通过
     */
    @Override
    public boolean verifyConfig(Function<StorageClientConfig, Boolean> verifyConfigAction) {
        return Boolean.TRUE.equals(verifyConfigAction.apply(config()));
    }

    /**
     * 校验当前客户端配置是否与指定配置一致
     * @param verifyConfig 待校验配置
     * @return 是否一致
     */
    @Override
    public boolean verifyConfig(StorageClientConfig verifyConfig) {
        return config.equals(verifyConfig);
    }

    /**
     * 根据文件名构建对象键
     * @param fileName 原始文件名
     * @return 对象键
     */
    @Override
    public String buildPathKey(String fileName) {
        return buildPathKey(null, fileName);
    }

    /**
     * 根据业务前缀和文件名构建对象键
     * @param businessPrefix 业务前缀
     * @param fileName 原始文件名
     * @return 对象键
     */
    @Override
    public String buildPathKey(String businessPrefix, String fileName) {
        String prefix = Stream.of(config.getPrefix(), businessPrefix)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().replaceAll("^/+|/+$", ""))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("/"));
        String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
        String ext = FileUtil.extName(fileName);
        String suffix = ext.isEmpty() ? "" : "." + ext;
        return (prefix.isEmpty() ? "" : prefix + "/") + datePath + "/" + IdUtil.fastSimpleUUID() + suffix;
    }

    /**
     * 执行自定义上传请求
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @param transferListeners 传输监听器集合
     * @param handleAsyncAction 上传完成处理函数
     * @param <T> 返回值类型
     * @return 上传处理结果
     */
    @Override
    public <T> T doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer,
            Collection<TransferListener> transferListeners,
            BiFunction<CompletedUpload, Throwable, T> handleAsyncAction) {
        try {
            return s3TransferManager.upload(request -> request
                            .requestBody(body)
                            .putObjectRequest(putObjectRequestBuilderConsumer)
                            .transferListeners(transferListeners))
                    .completionFuture()
                    .handleAsync(handleAsyncAction)
                    .join();
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 执行自定义上传请求
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @param handleAsyncAction 上传完成处理函数
     * @param <T> 返回值类型
     * @return 上传处理结果
     */
    @Override
    public <T> T doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer,
            BiFunction<CompletedUpload, Throwable, T> handleAsyncAction) {
        return doCustomUpload(body, putObjectRequestBuilderConsumer, null, handleAsyncAction);
    }

    /**
     * 执行自定义上传请求并返回统一异步结果
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @param transferListeners 传输监听器集合
     * @return 上传结果
     */
    @Override
    public HandleAsyncResult<PutObjectResponse> doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer,
            Collection<TransferListener> transferListeners) {
        return doCustomUpload(body, putObjectRequestBuilderConsumer, transferListeners, (completedUpload, throwable) -> {
            if (completedUpload == null) {
                return HandleAsyncResult.of(null, throwable);
            }
            return HandleAsyncResult.of(completedUpload.response(), throwable);
        });
    }

    /**
     * 执行自定义上传请求并返回统一异步结果
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @return 上传结果
     */
    @Override
    public HandleAsyncResult<PutObjectResponse> doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer) {
        return doCustomUpload(body, putObjectRequestBuilderConsumer, (Collection<TransferListener>) null);
    }

    /**
     * 上传本地路径文件到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param path 文件路径
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, Path path, Options options) {
        AsyncRequestBody body = AsyncRequestBody.fromFile(path);
        return bucketUpload(bucket, key, body, options);
    }

    /**
     * 上传本地路径文件到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param path 文件路径
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, Path path) {
        return bucketUpload(bucket, key, path, Options.builder());
    }

    /**
     * 上传文件到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 文件对象
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, File file, Options options) {
        return bucketUpload(bucket, key, file.toPath(), options);
    }

    /**
     * 上传文件到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 文件对象
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, File file) {
        return bucketUpload(bucket, key, file, Options.builder());
    }

    /**
     * 上传随机访问文件到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 随机访问文件
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, RandomAccessFile file, Options options) {
        try {
            return bucketUpload(bucket, key, file.getChannel(), file.length() - file.getFilePointer(), options);
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 上传随机访问文件到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 随机访问文件
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, RandomAccessFile file) {
        return bucketUpload(bucket, key, file, Options.builder());
    }

    /**
     * 上传可读通道数据到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param channel 可读通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(
            String bucket,
            String key,
            ReadableByteChannel channel,
            long contentLength,
            Options options) {
        InputStream in = Channels.newInputStream(channel);
        try {
            long size = contentLength;
            if (channel instanceof SeekableByteChannel byteChannel) {
                size = byteChannel.size() - byteChannel.position();
            }
            return bucketUpload(bucket, key, in, size, options);
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 上传可读通道数据到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param channel 可读通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, ReadableByteChannel channel, long contentLength) {
        return bucketUpload(bucket, key, channel, contentLength, Options.builder());
    }

    /**
     * 上传输入流数据到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(
            String bucket,
            String key,
            InputStream in,
            long contentLength,
            Options options) {
        if (contentLength >= 0) {
            return bucketUpload(bucket, key, AsyncRequestBody.fromInputStream(in, contentLength, asyncExecutor), options);
        }
        try {
            Path staged = Files.createTempFile("storage-upload-", ".part");
            try {
                Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
                return bucketUpload(bucket, key, staged, options);
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (IOException e) {
            throw toStorageException(e);
        }
    }

    /**
     * 上传输入流数据到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, InputStream in, long contentLength) {
        return bucketUpload(bucket, key, in, contentLength, Options.builder());
    }

    /**
     * 上传字节数组到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param data 字节数组
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, byte[] data, Options options) {
        AsyncRequestBody body = AsyncRequestBody.fromBytes(data);
        return bucketUpload(bucket, key, body, options);
    }

    /**
     * 上传字节数组到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param data 字节数组
     * @return 上传结果
     */
    @Override
    public PutObjectResult bucketUpload(String bucket, String key, byte[] data) {
        return bucketUpload(bucket, key, data, Options.builder());
    }

    /**
     * 执行指定存储桶的底层上传
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param body 上传请求体
     * @param options 上传选项
     * @return 上传结果
     */
    private PutObjectResult bucketUpload(String bucket, String key, AsyncRequestBody body, Options options) {
        Long contentLength = body.contentLength().orElse(options.getLength());
        String contentType = !StringUtils.hasText(options.getContentType()) ? body.contentType() : options.getContentType();
        String md5Digest = options.getMd5Digest();
        Map<String, String> metadata = options.getMetadata();
        Collection<TransferListener> transferListeners = options.getTransferListeners();
        HandleAsyncResult<PutObjectResponse> result = doCustomUpload(body, builder -> {
            builder.bucket(bucket)
                .key(key)
                .contentMD5(md5Digest)
                .contentType(contentType)
                .contentLength(contentLength)
                .metadata(metadata);
        }, transferListeners);
        if (result.isFailure()) {
            throw toStorageException(result.getError().orElseThrow());
        }
        PutObjectResponse response = result.getResult().orElseThrow(() -> new S3StorageException("对象存储未返回上传结果"));
        // 普通 PUT 响应可省略对象长度，使用本次请求的实际字节数
        Long size = response.size();
        if (size == null) {
            size = contentLength == null ? 0 : contentLength;
        }
        String bucketUrl = config.getBucketUrl(bucket);
        return PutObjectResult.form("%s/%s".formatted(bucketUrl, SdkHttpUtils.urlEncodeIgnoreSlashes(key)), key, response.eTag(), size);
    }

    /**
     * 执行自定义下载请求
     * @param getObjectRequestBuilderConsumer GetObject 请求构建回调
     * @param responseTransformer 下载响应转换器
     * @param transferListeners 传输监听器集合
     * @param <T> 下载结果类型
     * @return 下载结果
     */
    @Override
    public <T> T doCustomDownload(
            Consumer<GetObjectRequest.Builder> getObjectRequestBuilderConsumer,
            AsyncResponseTransformer<GetObjectResponse, T> responseTransformer,
            Collection<TransferListener> transferListeners) {
        try {
            DownloadRequest<T> downloadRequest = DownloadRequest.builder()
                .responseTransformer(responseTransformer)
                .getObjectRequest(getObjectRequestBuilderConsumer)
                .transferListeners(transferListeners)
                .build();
            return s3TransferManager.download(downloadRequest)
                .completionFuture()
                .join()
                .result();
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 将指定存储桶对象下载到订阅器
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param downloadSubscriber 下载订阅器
     * @return 下载结果
     */
    @Override
    public GetObjectResult bucketDownload(
            String bucket,
            String key,
            OutputStreamDownloadSubscriber downloadSubscriber) {
        try {
            ResponsePublisher<GetObjectResponse> publisher = doCustomDownload(builder -> builder.bucket(bucket).key(key), AsyncResponseTransformer.toPublisher(), null);
            GetObjectResult getObjectResult = buildGetObjectResult(key, publisher.response());
            publisher.subscribe(downloadSubscriber).join();
            return getObjectResult;
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 将指定存储桶对象下载到转换器
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param downloadTransformer 在输入流关闭前读取内容的回调
     * @param <T> 下载结果类型
     * @return 下载结果
     */
    @Override
    public <T> T bucketDownload(
            String bucket,
            String key,
            BiFunction<GetObjectResult, InputStream, T> downloadTransformer) {
        try (ResponseInputStream<GetObjectResponse> responseInputStream = doCustomDownload(builder -> builder.bucket(bucket).key(key), AsyncResponseTransformer.toBlockingInputStream(), null)) {
            GetObjectResponse response = responseInputStream.response();
            GetObjectResult getObjectResult = buildGetObjectResult(key, response);
            return downloadTransformer.apply(getObjectResult, responseInputStream);
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 将指定存储桶对象下载到本地路径
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param path 本地路径
     * @return 下载结果
     */
    @Override
    public GetObjectResult bucketDownload(String bucket, String key, Path path) {
        try (OutputStream out = Files.newOutputStream(path)) {
            return bucketDownload(bucket, key, out);
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 将指定存储桶对象下载到文件
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 本地文件
     * @return 下载结果
     */
    @Override
    public GetObjectResult bucketDownload(String bucket, String key, File file) {
        return bucketDownload(bucket, key, file.toPath());
    }

    /**
     * 将指定存储桶对象下载到随机访问文件
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 随机访问文件
     * @return 下载结果
     */
    @Override
    public GetObjectResult bucketDownload(String bucket, String key, RandomAccessFile file) {
        return bucketDownload(bucket, key, file.getChannel());
    }

    /**
     * 将指定存储桶对象下载到可写通道
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param channel 可写通道
     * @return 下载结果
     */
    @Override
    public GetObjectResult bucketDownload(String bucket, String key, WritableByteChannel channel) {
        return bucketDownload(bucket, key, OutputStreamDownloadSubscriber.create(channel));
    }

    /**
     * 将指定存储桶对象下载到输出流
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param out 由调用方管理的下载输出流
     * @return 下载结果
     */
    @Override
    public GetObjectResult bucketDownload(String bucket, String key, OutputStream out) {
        return bucketDownload(bucket, key, OutputStreamDownloadSubscriber.create(out));
    }

    /**
     * 根据 S3 响应构建下载结果
     * @param key 对象键
     * @param response S3 下载响应
     * @return 下载结果
     */
    private GetObjectResult buildGetObjectResult(String key, GetObjectResponse response) {
        return GetObjectResult.form(
            key,
            response.eTag(),
            response.lastModified().atOffset(ZoneOffset.UTC).toLocalDateTime(),
            response.contentLength(),
            response.contentType(),
            response.contentDisposition(),
            response.contentRange(),
            response.contentEncoding(),
            response.contentLanguage(),
            response.metadata()
        );
    }

    /**
     * 删除指定存储桶中的对象
     * @param bucket 存储桶名称
     * @param key 对象键
     * @return 是否删除成功
     */
    @Override
    public boolean bucketDelete(String bucket, String key) {
        try {
            s3AsyncClient.deleteObject(builder -> builder.bucket(bucket).key(key)).join();
            return true;
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 生成指定存储桶对象的下载预签名 URL
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param expiredTime 从签名时刻起算的有效时长
     * @return 预签名下载 URL
     */
    @Override
    public String bucketPresignGetUrl(String bucket, String key, Duration expiredTime) {
        if (useBucketBoundDomain(bucket)) {
            return bucketBoundDomainPresignUrl(SdkHttpMethod.GET, key, expiredTime, Collections.emptyMap());
        }
        try {
            return s3Presigner.presignGetObject(getObjectPresignRequestBuilder -> {
                    getObjectPresignRequestBuilder.signatureDuration(expiredTime)
                        .getObjectRequest(getObjectRequestBuilder -> getObjectRequestBuilder.bucket(bucket).key(key));
                })
                .url()
                .toExternalForm();
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 生成指定存储桶对象的上传预签名 URL
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param expiredTime 从签名时刻起算的有效时长
     * @param metadata 对象元数据
     * @return 预签名上传 URL
     */
    @Override
    public String bucketPresignPutUrl(
            String bucket,
            String key,
            Duration expiredTime,
            Map<String, String> metadata) {
        if (useBucketBoundDomain(bucket)) {
            return bucketBoundDomainPresignUrl(SdkHttpMethod.PUT, key, expiredTime, metadata);
        }
        try {
            return s3Presigner.presignPutObject(putObjectPresignRequestBuilder -> {
                    putObjectPresignRequestBuilder.signatureDuration(expiredTime)
                        .putObjectRequest(putObjectRequestBuilder -> putObjectRequestBuilder.bucket(bucket).key(key).metadata(metadata));
                })
                .url()
                .toExternalForm();
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 是否使用已绑定默认桶的自定义域名生成无桶名预签名 URL
     * @param bucket 存储桶名称
     * @return 是否使用自定义域名签名
     */
    private boolean useBucketBoundDomain(String bucket) {
        return config.getDomain() != null && config.getBucket().equals(bucket);
    }

    /**
     * 使用绑定默认桶的自定义域名生成预签名 URL，避免 S3 SDK 自动拼接桶名
     * @param method HTTP 方法
     * @param key 对象键
     * @param expiredTime 从签名时刻起算的有效时长
     * @param metadata 对象元数据
     * @return 预签名 URL
     */
    private String bucketBoundDomainPresignUrl(
            SdkHttpMethod method,
            String key,
            Duration expiredTime,
            Map<String, String> metadata) {
        try {
            URI domainUri = URI.create(config.getBucketUrl(config.getBucket()));
            SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .method(method)
                .uri(domainUri)
                .encodedPath(bucketBoundDomainPath(domainUri, key));
            if (metadata != null) {
                metadata.forEach((metadataKey, metadataValue) -> {
                    if (StringUtils.hasText(metadataKey)) {
                        requestBuilder.putHeader("x-amz-meta-" + metadataKey, String.valueOf(metadataValue));
                    }
                });
            }
            AwsCredentialsIdentity credentials = AwsCredentialsIdentity.create(
                config.getAccessKey(),
                config.getSecretKey()
            );
            Clock signingClock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
            return AwsV4HttpSigner.create()
                .sign(SignRequest.builder(credentials)
                    .request(requestBuilder.build())
                    .putProperty(AwsV4HttpSigner.REGION_NAME, config.getRegion())
                    .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, "s3")
                    .putProperty(AwsV4FamilyHttpSigner.AUTH_LOCATION, AwsV4FamilyHttpSigner.AuthLocation.QUERY_STRING)
                    .putProperty(AwsV4FamilyHttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                    .putProperty(AwsV4FamilyHttpSigner.EXPIRATION_DURATION, expiredTime)
                    .putProperty(HttpSigner.SIGNING_CLOCK, signingClock)
                    .putProperty(AwsV4FamilyHttpSigner.DOUBLE_URL_ENCODE, false)
                    .putProperty(AwsV4FamilyHttpSigner.NORMALIZE_PATH, false)
                    .build())
                .request()
                .getUri()
                .toString();
        } catch (Exception e) {
            throw toStorageException(e);
        }
    }

    /**
     * 构建绑定桶域名下的对象访问路径
     * @param domainUri 自定义域名 URI
     * @param key 对象键
     * @return 编码后的访问路径
     */
    private String bucketBoundDomainPath(URI domainUri, String key) {
        String basePath = Optional.ofNullable(domainUri.getRawPath())
            .filter(StringUtils::hasText)
            .filter(path -> !"/".equals(path))
            .orElse("");
        String objectPath = SdkHttpUtils.urlEncodeIgnoreSlashes(key);
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (!basePath.endsWith("/")) {
            basePath = basePath + "/";
        }
        return basePath + objectPath;
    }

    /**
     * 上传本地路径文件到默认存储桶
     * @param key 对象键
     * @param path 文件路径
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, Path path, Options options) {
        return bucketUpload(defaultBucket(), key, path, options);
    }

    /**
     * 上传本地路径文件到默认存储桶
     * @param key 对象键
     * @param path 文件路径
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, Path path) {
        return bucketUpload(defaultBucket(), key, path);
    }

    /**
     * 上传文件到默认存储桶
     * @param key 对象键
     * @param file 文件对象
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, File file, Options options) {
        return bucketUpload(defaultBucket(), key, file, options);
    }

    /**
     * 上传文件到默认存储桶
     * @param key 对象键
     * @param file 文件对象
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, File file) {
        return bucketUpload(defaultBucket(), key, file);
    }

    /**
     * 上传随机访问文件到默认存储桶
     * @param key 对象键
     * @param file 随机访问文件
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, RandomAccessFile file, Options options) {
        return bucketUpload(defaultBucket(), key, file, options);
    }

    /**
     * 上传随机访问文件到默认存储桶
     * @param key 对象键
     * @param file 随机访问文件
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, RandomAccessFile file) {
        return bucketUpload(defaultBucket(), key, file);
    }

    /**
     * 上传可读通道数据到默认存储桶
     * @param key 对象键
     * @param channel 可读通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, ReadableByteChannel channel, long contentLength, Options options) {
        return bucketUpload(defaultBucket(), key, channel, contentLength, options);
    }

    /**
     * 上传可读通道数据到默认存储桶
     * @param key 对象键
     * @param channel 可读通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, ReadableByteChannel channel, long contentLength) {
        return bucketUpload(defaultBucket(), key, channel, contentLength);
    }

    /**
     * 上传输入流数据到默认存储桶
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, InputStream in, long contentLength, Options options) {
        return bucketUpload(defaultBucket(), key, in, contentLength, options);
    }

    /**
     * 上传输入流数据到默认存储桶
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, InputStream in, long contentLength) {
        return bucketUpload(defaultBucket(), key, in, contentLength);
    }

    /**
     * 上传字节数组到默认存储桶
     * @param key 对象键
     * @param data 字节数组
     * @param options 上传选项
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, byte[] data, Options options) {
        return bucketUpload(defaultBucket(), key, data, options);
    }

    /**
     * 上传字节数组到默认存储桶
     * @param key 对象键
     * @param data 字节数组
     * @return 上传结果
     */
    @Override
    public PutObjectResult upload(String key, byte[] data) {
        return bucketUpload(defaultBucket(), key, data);
    }

    /**
     * 将默认存储桶对象下载到订阅器
     * @param key 对象键
     * @param downloadSubscriber 下载订阅器
     * @return 下载结果
     */
    @Override
    public GetObjectResult download(String key, OutputStreamDownloadSubscriber downloadSubscriber) {
        return bucketDownload(defaultBucket(), key, downloadSubscriber);
    }

    /**
     * 将默认存储桶对象下载到转换器
     * @param key 对象键
     * @param downloadTransformer 在输入流关闭前读取内容的回调
     * @param <T> 下载结果类型
     * @return 下载结果
     */
    @Override
    public <T> T download(String key, BiFunction<GetObjectResult, InputStream, T> downloadTransformer) {
        return bucketDownload(defaultBucket(), key, downloadTransformer);
    }

    /**
     * 将默认存储桶对象下载到本地路径
     * @param key 对象键
     * @param path 本地路径
     * @return 下载结果
     */
    @Override
    public GetObjectResult download(String key, Path path) {
        return bucketDownload(defaultBucket(), key, path);
    }

    /**
     * 将默认存储桶对象下载到文件
     * @param key 对象键
     * @param file 本地文件
     * @return 下载结果
     */
    @Override
    public GetObjectResult download(String key, File file) {
        return bucketDownload(defaultBucket(), key, file);
    }

    /**
     * 将默认存储桶对象下载到随机访问文件
     * @param key 对象键
     * @param file 随机访问文件
     * @return 下载结果
     */
    @Override
    public GetObjectResult download(String key, RandomAccessFile file) {
        return bucketDownload(defaultBucket(), key, file);
    }

    /**
     * 将默认存储桶对象下载到可写通道
     * @param key 对象键
     * @param channel 可写通道
     * @return 下载结果
     */
    @Override
    public GetObjectResult download(String key, WritableByteChannel channel) {
        return bucketDownload(defaultBucket(), key, channel);
    }

    /**
     * 将默认存储桶对象下载到输出流
     * @param key 对象键
     * @param out 由调用方管理的下载输出流
     * @return 下载结果
     */
    @Override
    public GetObjectResult download(String key, OutputStream out) {
        return bucketDownload(defaultBucket(), key, out);
    }

    /**
     * 删除默认存储桶中的对象
     * @param key 对象键
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String key) {
        return bucketDelete(defaultBucket(), key);
    }

    /**
     * 生成默认存储桶对象的下载预签名 URL
     * @param key 对象键
     * @param expiredTime 从签名时刻起算的有效时长
     * @return 预签名下载 URL
     */
    @Override
    public String presignGetUrl(String key, Duration expiredTime) {
        return bucketPresignGetUrl(defaultBucket(), key, expiredTime);
    }

    /**
     * 生成默认存储桶对象的上传预签名 URL
     * @param key 对象键
     * @param expiredTime 从签名时刻起算的有效时长
     * @param metadata 对象元数据
     * @return 预签名上传 URL
     */
    @Override
    public String presignPutUrl(String key, Duration expiredTime, Map<String, String> metadata) {
        return bucketPresignPutUrl(defaultBucket(), key, expiredTime, metadata);
    }

    /**
     * 打开默认桶对象，由调用方关闭流
     * @param key 对象键
     * @return 对象输入流
     */
    @Override
    public InputStream open(String key) {
        return bucketOpen(defaultBucket(), key);
    }

    /**
     * 打开指定桶对象，由调用方关闭流
     * @param bucket 桶名
     * @param key 对象键
     * @return 对象输入流
     */
    @Override
    public InputStream bucketOpen(String bucket, String key) {
        try {
            return s3AsyncClient.getObject(request -> request.bucket(bucket).key(key),
                    AsyncResponseTransformer.toBlockingInputStream()).join();
        } catch (CompletionException e) {
            throw toStorageException(e);
        }
    }

    /**
     * 获取默认存储桶名称
     * @return 默认存储桶名称
     */
    private String defaultBucket() {
        if (!StringUtils.hasText(config.getBucket())) {
            throw new S3StorageException("默认存储桶未配置");
        }
        return config.getBucket();
    }

    /**
     * 转换为统一的 S3 存储异常
     * @param e 原始异常
     * @return S3 存储异常
     */
    private S3StorageException toStorageException(Throwable e) {
        Throwable cause = unwrapAsyncException(e);
        if (cause instanceof S3StorageException ex) {
            return ex;
        }
        return new S3StorageException("对象存储操作失败", cause);
    }

    /**
     * 解包异步执行异常
     * @param e 原始异常
     * @return 根因异常
     */
    private Throwable unwrapAsyncException(Throwable e) {
        Throwable cause = e;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * 关闭底层 S3 客户端资源
     */
    @Override
    public synchronized void close() {
        try (S3Presigner signer = s3Presigner;
             S3AsyncClient client = s3AsyncClient;
             S3TransferManager transfers = s3TransferManager) {
            initialized = false;
        } finally {
            s3TransferManager = null;
            s3AsyncClient = null;
            s3Presigner = null;
            if (asyncExecutor != null) {
                asyncExecutor.shutdown();
                asyncExecutor = null;
            }
        }
    }
}
