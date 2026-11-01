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
     * 在业务事务内锁定存储配置，协调文件上传、清理与配置变更
     * @param key 存储配置名
     * @return 已锁定的配置记录，不存在时为 null
     */
    StorageConfig getByIdForShare(String key);
}
