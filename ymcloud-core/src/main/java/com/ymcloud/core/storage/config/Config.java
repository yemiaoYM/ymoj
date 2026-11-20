package com.ymcloud.core.storage.config;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 支持复制和基于现有值重建的配置契约
 */
public interface Config<T, B> {

    /**
     * 复制当前配置
     * @return 与当前值相同的新配置
     */
    T copy();

    /**
     * 使用当前配置值初始化构建器
     * @return 仅影响新配置的构建器
     */
    B toBuilder();
}
