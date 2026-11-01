package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.core.web.mapper.StorageConfigMapper;
import com.ymcloud.pojo.entity.StorageConfig;
import org.springframework.stereotype.Service;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置实体访问实现
 */
@Service
public class StorageConfigEntityServiceImpl extends ServiceImpl<StorageConfigMapper, StorageConfig>
        implements StorageConfigEntityService {

    /**
     * 在业务事务内锁定存储配置，协调文件上传、清理与配置变更
     * @param key 存储配置名
     * @return 已锁定的配置记录，不存在时为 null
     */
    @Override
    public StorageConfig getByIdForShare(String key) {
        return baseMapper.getByIdForShare(key);
    }
}
