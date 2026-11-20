package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.StorageConfig;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置实体访问契约
 */
public interface StorageConfigEntityService extends IService<StorageConfig> {

    /**
     * 读取并锁定当前默认配置，保证上传登记期间存储位置稳定
     * @return 已锁定的配置记录，不存在时为 null
     */
    StorageConfig getDefaultForShare();

    /**
     * 读取文件登记时使用的存储配置
     * @param key 文件所属的存储配置标识
     * @return 配置记录，已删除时为空
     */
    StorageConfig getByConfigKey(String key);
}
