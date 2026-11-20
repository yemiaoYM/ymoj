package com.ymcloud.core.storage.config;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: S3 阻塞输入流读取执行器配置
 */
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
public final class StorageAsyncExecutorConfig
        implements Config<StorageAsyncExecutorConfig, StorageAsyncExecutorConfig.StorageAsyncExecutorConfigBuilder> {

    public static final StorageAsyncExecutorConfig DEFAULT = StorageAsyncExecutorConfig.builder().build();

    @Builder.Default
    private final int corePoolSize = Runtime.getRuntime().availableProcessors();

    /**
     * 创建独立的配置副本
     * @return 当前配置的副本
     */
    @Override
    public StorageAsyncExecutorConfig copy() {
        return toBuilder().build();
    }
}
