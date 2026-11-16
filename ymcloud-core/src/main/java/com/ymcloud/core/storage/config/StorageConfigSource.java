package com.ymcloud.core.storage.config;

import com.ymcloud.pojo.entity.StorageConfig;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 存储客户端读取动态配置的边界，不承担文件引用管理
 */
public interface StorageConfigSource {

    /**
     * 获取当前默认存储配置标识
     * @return 默认存储配置标识
     */
    String active();

    /**
     * 读取指定动态配置
     * @param key 配置标识
     * @return 动态配置，未配置时为空
     */
    StorageConfig find(String key);

    /**
     * 将动态配置解析为客户端参数
     * @param config 已读取的存储配置
     * @return 包含解密凭据的内部客户端参数，不对外返回
     */
    StorageProperties.Profile profile(StorageConfig config);
}
