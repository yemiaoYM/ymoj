package com.ymcloud.core.storage;

import com.ymcloud.common.exception.file.FileException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地驱动保留给历史兼容和迁移回退。
 */
public class LocalObjectStorageService implements ObjectStorageService {

    private final Path root;

    public LocalObjectStorageService(Path root) {
        this.root = root;
    }

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

    @Override
    public InputStream open(String bucket, String key) {
        try {
            return Files.newInputStream(StoragePaths.resolve(root, bucket + "/" + key));
        } catch (IOException e) {
            throw new FileException("本地读取文件失败", e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            Files.deleteIfExists(StoragePaths.resolve(root, bucket + "/" + key));
        } catch (IOException e) {
            throw new FileException("本地删除文件失败", e);
        }
    }
}
