package com.ymcloud.core.storage.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 下载对象的响应头及元数据
 */
@RequiredArgsConstructor
@Getter
public final class GetObjectResult {

    private final String key;
    private final String eTag;
    private final LocalDateTime lastModified;
    private final long size;
    private final String contentType;
    private final String contentDisposition;
    private final String contentRange;
    private final String contentEncoding;
    private final String contentLanguage;
    private final Map<String, String> metadata;

    /**
     * 构建文件对象获取结果
     * @param key 对象 Key
     * @param eTag 对象 ETag
     * @param lastModified 最后修改时间
     * @param size 对象大小
     * @param contentType 内容类型
     * @param contentDisposition 内容处置方式
     * @param contentRange 内容范围
     * @param contentEncoding 内容编码
     * @param contentLanguage 内容语言
     * @param metadata 自定义元数据
     * @return 文件对象获取结果
     */
    public static GetObjectResult form(
            String key, String eTag, LocalDateTime lastModified, long size, String contentType,
            String contentDisposition, String contentRange, String contentEncoding, String contentLanguage,
            Map<String, String> metadata) {
        return new GetObjectResult(key, eTag, lastModified, size, contentType, contentDisposition,
                contentRange, contentEncoding, contentLanguage, metadata);
    }
}
