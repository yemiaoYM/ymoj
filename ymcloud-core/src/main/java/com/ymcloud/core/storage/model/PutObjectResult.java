package com.ymcloud.core.storage.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 上传对象的地址、对象键、ETag 与字节数
 */
@RequiredArgsConstructor
@Getter
public final class PutObjectResult {

    private final String url;
    private final String key;
    private final String eTag;
    private final long size;

    /**
     * 构建文件上传结果
     * @param url 文件地址
     * @param key 文件标识
     * @param eTag 文件 ETag
     * @param size 文件大小
     * @return 文件上传结果
     */
    public static PutObjectResult form(String url, String key, String eTag, long size) {
        return new PutObjectResult(url, key, eTag, size);
    }
}
