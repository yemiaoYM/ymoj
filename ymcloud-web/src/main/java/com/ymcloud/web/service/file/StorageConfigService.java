package com.ymcloud.web.service.file;

import com.ymcloud.core.storage.config.StorageConfigSource;
import com.ymcloud.core.storage.config.StorageProperties;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.StorageConfig;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 动态存储配置管理及文件引用核验入口
 */
public interface StorageConfigService extends StorageConfigSource {

    /**
     * 读取当前默认存储配置标识
     * @return 默认配置标识
     */
    String active();

    /**
     * 按标识读取动态存储配置
     * @param key 存储配置标识
     * @return 动态配置，不存在时为空
     */
    StorageConfig find(String key);

    /**
     * 列出动态存储配置
     * @return 动态配置列表
     */
    List<StorageConfig> list();

    /**
     * 锁定存储配置状态以串行化配置修改
     */
    void lockForWrite();

    /**
     * 判断存储配置是否被文件引用
     * @param key 存储配置标识
     * @return 是否存在文件引用
     */
    boolean referenced(String key);

    /**
     * 读取存储配置关联的文件用于恢复核验
     * @param key 存储配置标识
     * @return 按文件编号排序的引用列表
     */
    List<File> referencedFiles(String key);

    /**
     * 解密并组装内部客户端配置
     * @param config 待处理的动态存储配置
     * @return 仅内部使用的客户端配置
     */
    StorageProperties.Profile profile(StorageConfig config);

    /**
     * 保存动态存储配置，调用方持有配置状态锁
     * @param config 待处理的动态存储配置
     * @param exists 是否更新已有配置
     */
    void save(StorageConfig config, boolean exists);

    /**
     * 删除指定存储配置
     * @param key 存储配置标识
     */
    void remove(String key);

    /**
     * 在配置写事务内更新唯一默认配置
     * @param key 存储配置标识
     */
    void activate(String key);
}
