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
     * 读取并锁定当前默认配置，保证上传登记期间存储位置稳定
     * @return 已锁定的配置记录，不存在时为 null
     */
    @Override
    public StorageConfig getDefaultForShare() {
        return baseMapper.getDefaultForShare();
    }

    /**
     * 根据配置标识查找已有文件的存储位置
     * @param key 文件所属的存储配置标识
     * @return 配置记录，已删除时为空
     */
    @Override
    public StorageConfig getByConfigKey(String key) {
        return lambdaQuery()
                .eq(StorageConfig::getConfigKey, key)
                .one();
    }
}
