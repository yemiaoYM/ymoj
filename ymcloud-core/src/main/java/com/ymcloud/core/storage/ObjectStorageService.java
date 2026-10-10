package com.ymcloud.core.storage;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 仅负责对象字节，不处理业务授权及数据库事务。
 */
public interface ObjectStorageService extends AutoCloseable {

    void put(String bucket, String key, Path source, String contentType, String sha256);

    InputStream open(String bucket, String key);

    void delete(String bucket, String key);

    @Override
    default void close() {
    }
}
