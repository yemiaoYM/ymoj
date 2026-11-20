package com.ymcloud.core.storage.client;

import com.ymcloud.core.storage.config.StorageClientConfig;
import com.ymcloud.core.storage.io.OutputStreamDownloadSubscriber;
import com.ymcloud.core.storage.model.GetObjectResult;
import com.ymcloud.core.storage.model.HandleAsyncResult;
import com.ymcloud.core.storage.model.Options;
import com.ymcloud.core.storage.model.PutObjectResult;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.transfer.s3.model.CompletedUpload;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: S3 对象客户端，提供默认桶及指定桶操作
 */
public interface StorageClient extends AutoCloseable {

    /**
     * 获取初始化时绑定的客户端标识
     * @return 存储配置标识
     */
    String clientId();

    /**
     * 读取客户端的配置副本
     * @return 用于比对或重建客户端的配置
     */
    StorageClientConfig config();

    /**
     * 判断底层传输资源是否已经就绪
     * @return 已初始化且尚未关闭时为 true
     */
    boolean isInitialized();

    /**
     * 创建传输资源，已初始化的客户端重复调用时复用现有连接
     */
    void initialize();

    /**
     * 在配置副本上执行调用方的比较规则
     * @param verifyConfigAction 配置比较函数
     * @return 函数是否确认配置匹配
     */
    boolean verifyConfig(Function<StorageClientConfig, Boolean> verifyConfigAction);

    /**
     * 比较用于创建连接的完整配置
     * @param verifyConfig 新读取的配置
     * @return 是否可以复用当前客户端
     */
    boolean verifyConfig(StorageClientConfig verifyConfig);

    /**
     * 执行自定义上传请求
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @param transferListeners 传输监听器集合
     * @param handleAsyncAction 上传完成后的结果处理函数
     * @param <T> 返回值类型
     * @return 处理后的结果
     */
    <T> T doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer,
            Collection<TransferListener> transferListeners,
            BiFunction<CompletedUpload, Throwable, T> handleAsyncAction);

    /**
     * 执行自定义上传请求
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @param handleAsyncAction 上传完成后的结果处理函数
     * @param <T> 返回值类型
     * @return 处理后的结果
     */
    <T> T doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer,
            BiFunction<CompletedUpload, Throwable, T> handleAsyncAction);

    /**
     * 执行自定义上传请求，并返回统一异步处理结果
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @param transferListeners 传输监听器集合
     * @return 上传结果
     */
    HandleAsyncResult<PutObjectResponse> doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer,
            Collection<TransferListener> transferListeners);

    /**
     * 执行自定义上传请求，并返回统一异步处理结果
     * @param body 上传请求体
     * @param putObjectRequestBuilderConsumer PutObject 请求构建回调
     * @return 上传结果
     */
    HandleAsyncResult<PutObjectResponse> doCustomUpload(
            AsyncRequestBody body,
            Consumer<PutObjectRequest.Builder> putObjectRequestBuilderConsumer);

    /**
     * 将本地路径对应的文件上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param path 文件路径
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, Path path, Options options);

    /**
     * 将本地路径对应的文件上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param path 文件路径
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, Path path);

    /**
     * 将文件上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 文件对象
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, File file, Options options);

    /**
     * 将文件上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 文件对象
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, File file);

    /**
     * 将随机访问文件上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 随机访问文件
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, RandomAccessFile file, Options options);

    /**
     * 将随机访问文件上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 随机访问文件
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, RandomAccessFile file);

    /**
     * 将可读通道中的数据上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param channel 数据通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult bucketUpload(
            String bucket,
            String key,
            ReadableByteChannel channel,
            long contentLength,
            Options options);

    /**
     * 将可读通道中的数据上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param channel 数据通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, ReadableByteChannel channel, long contentLength);

    /**
     * 将可读通道中的数据上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, InputStream in, long contentLength, Options options);

    /**
     * 将输入流中的数据上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, InputStream in, long contentLength);

    /**
     * 将字节数组上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param data 字节数组
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, byte[] data, Options options);

    /**
     * 将字节数组上传到指定存储桶
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param data 字节数组
     * @return 上传结果
     */
    PutObjectResult bucketUpload(String bucket, String key, byte[] data);

    /**
     * 执行自定义下载请求
     * @param getObjectRequestBuilderConsumer GetObject 请求构建回调
     * @param responseTransformer 下载响应转换器
     * @param transferListeners 传输监听器集合
     * @param <T> 下载结果类型
     * @return 下载结果
     */
    <T> T doCustomDownload(
            Consumer<GetObjectRequest.Builder> getObjectRequestBuilderConsumer,
            AsyncResponseTransformer<GetObjectResponse, T> responseTransformer,
            Collection<TransferListener> transferListeners);

    /**
     * 将指定存储桶中的对象下载到订阅器
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param downloadSubscriber 下载订阅器
     * @return 下载结果
     */
    GetObjectResult bucketDownload(String bucket, String key, OutputStreamDownloadSubscriber downloadSubscriber);

    /**
     * 在默认桶的对象流关闭前执行下载回调
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param downloadTransformer 在输入流关闭前读取内容的回调
     * @param <T> 回调返回的业务结果类型
     * @return 下载结果
     */
    <T> T bucketDownload(String bucket, String key, BiFunction<GetObjectResult, InputStream, T> downloadTransformer);

    /**
     * 将指定存储桶中的对象下载到本地路径
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param path 本地路径
     * @return 下载结果
     */
    GetObjectResult bucketDownload(String bucket, String key, Path path);

    /**
     * 将指定存储桶中的对象下载到文件
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 本地文件
     * @return 下载结果
     */
    GetObjectResult bucketDownload(String bucket, String key, File file);

    /**
     * 将指定存储桶中的对象下载到随机访问文件
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param file 随机访问文件
     * @return 下载结果
     */
    GetObjectResult bucketDownload(String bucket, String key, RandomAccessFile file);

    /**
     * 将指定存储桶中的对象下载到可写通道
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param channel 可写通道
     * @return 下载结果
     */
    GetObjectResult bucketDownload(String bucket, String key, WritableByteChannel channel);

    /**
     * 将指定存储桶中的对象下载到输出流
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param out 由调用方管理的下载输出流
     * @return 下载结果
     */
    GetObjectResult bucketDownload(String bucket, String key, OutputStream out);

    /**
     * 删除指定存储桶中的对象
     * @param bucket 存储桶名称
     * @param key 对象键
     * @return 是否删除成功
     */
    boolean bucketDelete(String bucket, String key);

    /**
     * 生成指定存储桶中文件下载的预签名 URL
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param expiredTime URL 过期时间
     * @return 预签名下载 URL
     */
    String bucketPresignGetUrl(String bucket, String key, Duration expiredTime);

    /**
     * 生成指定存储桶中文件上传的预签名 URL
     * @param bucket 存储桶名称
     * @param key 对象键
     * @param expiredTime URL 过期时间
     * @param metadata 对象元数据
     * @return 预签名上传 URL
     */
    String bucketPresignPutUrl(String bucket, String key, Duration expiredTime, Map<String, String> metadata);

    /**
     * 将本地路径对应的文件上传到默认存储桶
     * @param key 对象键
     * @param path 文件路径
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult upload(String key, Path path, Options options);

    /**
     * 将本地路径对应的文件上传到默认存储桶
     * @param key 对象键
     * @param path 文件路径
     * @return 上传结果
     */
    PutObjectResult upload(String key, Path path);

    /**
     * 将文件上传到默认存储桶
     * @param key 对象键
     * @param file 文件对象
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult upload(String key, File file, Options options);

    /**
     * 将文件上传到默认存储桶
     * @param key 对象键
     * @param file 文件对象
     * @return 上传结果
     */
    PutObjectResult upload(String key, File file);

    /**
     * 将随机访问文件上传到默认存储桶
     * @param key 对象键
     * @param file 随机访问文件
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult upload(String key, RandomAccessFile file, Options options);

    /**
     * 将随机访问文件上传到默认存储桶
     * @param key 对象键
     * @param file 随机访问文件
     * @return 上传结果
     */
    PutObjectResult upload(String key, RandomAccessFile file);

    /**
     * 将可读通道中的数据上传到默认存储桶
     * @param key 对象键
     * @param channel 数据通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult upload(String key, ReadableByteChannel channel, long contentLength, Options options);

    /**
     * 将可读通道中的数据上传到默认存储桶
     * @param key 对象键
     * @param channel 数据通道
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    PutObjectResult upload(String key, ReadableByteChannel channel, long contentLength);

    /**
     * 将输入流中的数据上传到默认存储桶
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult upload(String key, InputStream in, long contentLength, Options options);

    /**
     * 将输入流中的数据上传到默认存储桶
     * @param key 对象键
     * @param in 由调用方关闭的上传流
     * @param contentLength 上传字节数，负值表示未知长度
     * @return 上传结果
     */
    PutObjectResult upload(String key, InputStream in, long contentLength);

    /**
     * 将字节数组上传到默认存储桶
     * @param key 对象键
     * @param data 字节数组
     * @param options 媒体类型、校验值及传输选项
     * @return 上传结果
     */
    PutObjectResult upload(String key, byte[] data, Options options);

    /**
     * 将字节数组上传到默认存储桶
     * @param key 对象键
     * @param data 字节数组
     * @return 上传结果
     */
    PutObjectResult upload(String key, byte[] data);

    /**
     * 将默认存储桶中的对象下载到订阅器
     * @param key 对象键
     * @param downloadSubscriber 下载订阅器
     * @return 下载结果
     */
    GetObjectResult download(String key, OutputStreamDownloadSubscriber downloadSubscriber);

    /**
     * 在默认桶的对象流关闭前执行下载回调
     * @param key 对象键
     * @param downloadTransformer 在输入流关闭前读取内容的回调
     * @param <T> 回调返回的业务结果类型
     * @return 下载结果
     */
    <T> T download(String key, BiFunction<GetObjectResult, InputStream, T> downloadTransformer);

    /**
     * 将默认存储桶中的对象下载到本地路径
     * @param key 对象键
     * @param path 本地路径
     * @return 下载结果
     */
    GetObjectResult download(String key, Path path);

    /**
     * 将默认存储桶中的对象下载到文件
     * @param key 对象键
     * @param file 本地文件
     * @return 下载结果
     */
    GetObjectResult download(String key, File file);

    /**
     * 将默认存储桶中的对象下载到随机访问文件
     * @param key 对象键
     * @param file 随机访问文件
     * @return 下载结果
     */
    GetObjectResult download(String key, RandomAccessFile file);

    /**
     * 将默认存储桶中的对象下载到可写通道
     * @param key 对象键
     * @param channel 可写通道
     * @return 下载结果
     */
    GetObjectResult download(String key, WritableByteChannel channel);

    /**
     * 将默认存储桶中的对象下载到输出流
     * @param key 对象键
     * @param out 由调用方管理的下载输出流
     * @return 下载结果
     */
    GetObjectResult download(String key, OutputStream out);

    /**
     * 删除默认存储桶中的对象
     * @param key 对象键
     * @return 是否删除成功
     */
    boolean delete(String key);

    /**
     * 生成默认存储桶中文件下载的预签名 URL
     * @param key 对象键
     * @param expiredTime URL 过期时间
     * @return 预签名下载 URL
     */
    String presignGetUrl(String key, Duration expiredTime);

    /**
     * 生成默认存储桶中文件上传的预签名 URL
     * @param key 对象键
     * @param expiredTime URL 过期时间
     * @param metadata 对象元数据
     * @return 预签名上传 URL
     */
    String presignPutUrl(String key, Duration expiredTime, Map<String, String> metadata);

    /**
     * 根据客户端配置生成默认对象键
     * @param fileName 原始文件名
     * @return 对象键
     */
    String buildPathKey(String fileName);

    /**
     * 根据业务前缀和客户端默认前缀生成对象键
     * @param businessPrefix 业务前缀
     * @param fileName 原始文件名
     * @return 对象键
     */
    String buildPathKey(String businessPrefix, String fileName);

    /**
     * 打开默认桶的对象流，调用方负责关闭
     * @param key 对象键
     * @return 可逐段读取的输入流
     */
    InputStream open(String key);

    /**
     * 打开指定桶的对象流
     * @param bucket 桶名
     * @param key 对象键
     * @return 由调用方关闭的输入流
     */
    InputStream bucketOpen(String bucket, String key);

    /**
     * 释放传输资源
     */
    @Override
    void close();
}
