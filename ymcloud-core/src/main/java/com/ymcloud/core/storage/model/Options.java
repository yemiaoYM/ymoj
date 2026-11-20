package com.ymcloud.core.storage.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 上传请求的媒体类型、长度、校验和、元数据及监听器
 */
@Data
@EqualsAndHashCode
@Accessors(chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Options {

    /**
     * 文件长度
     */
    private Long length;

    /**
     * 文件MD5摘要
     */
    private String md5Digest;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * 元数据
     */
    private Map<String, String> metadata;

    /**
     * 传输监听器
     */
    private Collection<TransferListener> transferListeners;

    /**
     * 添加对象元数据
     * @param key 元数据名称
     * @param value 元数据值
     * @return 当前选项
     */
    public Options addMetadataItem(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 添加传输进度监听器
     * @param transferListener 进度监听器
     * @return 当前选项
     */
    public Options addTransferListener(TransferListener transferListener) {
        if (this.transferListeners == null) {
            this.transferListeners = new ArrayList<>();
        }
        this.transferListeners.add(transferListener);
        return this;
    }

    /**
     * 创建可选项对象
     */
    public static Options builder() {
        return create();
    }

    /**
     * 创建可选项对象
     */
    public static Options create() {
        return new Options();
    }

}
