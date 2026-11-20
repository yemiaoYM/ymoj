package com.ymcloud.core.storage.exception;

import com.ymcloud.common.exception.file.FileException;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 对象存储访问异常，保留底层传输原因
 */
public class S3StorageException extends FileException {

    /**
     * 描述缺失的配置或存储响应
     * @param message 可对外展示的错误消息
     */
    public S3StorageException(String message) {
        super(message);
    }

    /**
     * 将传输异常转换为项目文件异常
     * @param message 可对外展示的错误消息
     * @param cause 底层 SDK 或 I/O 异常
     */
    public S3StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
