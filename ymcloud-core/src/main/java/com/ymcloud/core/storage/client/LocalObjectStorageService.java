package com.ymcloud.core.storage.client;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.util.StoragePaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 在配置的本地根目录中保存、读取和删除对象文件
 */
public class LocalObjectStorageService implements ObjectStorageService {

    private final Path root;

    /**
     * 固定本地对象存储根目录
     * @param root 已初始化的本地存储根目录
     */
    public LocalObjectStorageService(Path root) {
        this.root = root;
    }

    /**
     * 上传本地文件，由调用方提供对象位置及文件摘要
     * @param bucket 本地存储桶目录名称
     * @param key 桶内对象键
     * @param source 待上传的本地文件
     * @param contentType 文件媒体类型，本地驱动不单独保存该元数据
     * @param sha256 文件内容的 SHA-256 摘要，由上层负责登记与回读验证
     */
    @Override
    public void put(String bucket, String key, Path source, String contentType, String sha256) {
        Path target = StoragePaths.resolve(root, bucket + "/" + key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        } catch (IOException e) {
            throw new FileException("本地保存文件失败", e);
        }
    }

    /**
     * 打开本地文件的内容流
     * @param bucket 本地存储桶目录名称
     * @param key 桶内对象键
     * @return 内容流，由调用方关闭
     */
    @Override
    public InputStream open(String bucket, String key) {
        try {
            return Files.newInputStream(StoragePaths.resolve(root, bucket + "/" + key));
        } catch (IOException e) {
            throw new FileException("本地读取文件失败", e);
        }
    }

    /**
     * 删除指定本地文件，文件不存在时无需处理
     * @param bucket 本地存储桶目录名称
     * @param key 桶内对象键
     */
    @Override
    public void delete(String bucket, String key) {
        try {
            Files.deleteIfExists(StoragePaths.resolve(root, bucket + "/" + key));
        } catch (IOException e) {
            throw new FileException("本地删除文件失败", e);
        }
    }
}
