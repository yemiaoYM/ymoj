package com.ymcloud.core.storage.client;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 仅负责对象字节，不处理业务授权及数据库事务
 */
public interface ObjectStorageService extends AutoCloseable {

    /**
     * 写入可信对象位置，不决定公开性或业务归属
     * @param bucket 上层确定的存储桶
     * @param key 上层确定的对象键
     * @param source 已完成校验的本地暂存文件
     * @param contentType 对象媒体类型
     * @param sha256 对象内容的 SHA-256
     */
    void put(String bucket, String key, Path source, String contentType, String sha256);

    /**
     * 打开指定对象，不进行业务授权
     * @param bucket 可信存储桶
     * @param key 可信对象键
     * @return 调用方负责关闭的输入流
     */
    InputStream open(String bucket, String key);

    /**
     * 删除指定对象，引用与权限由上层检查
     * @param bucket 可信存储桶
     * @param key 可信对象键
     */
    void delete(String bucket, String key);

    /**
     * 释放客户端资源，无独立资源的驱动无需处理
     */
    @Override
    default void close() {}
}
