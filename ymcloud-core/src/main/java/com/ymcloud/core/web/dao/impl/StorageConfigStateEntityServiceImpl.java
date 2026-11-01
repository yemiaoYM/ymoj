package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.StorageConfigStateEntityService;
import com.ymcloud.core.web.mapper.StorageConfigStateMapper;
import com.ymcloud.pojo.entity.StorageConfigState;
import org.springframework.stereotype.Service;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置状态实体访问实现
 */
@Service
public class StorageConfigStateEntityServiceImpl
        extends ServiceImpl<StorageConfigStateMapper, StorageConfigState>
        implements StorageConfigStateEntityService {

    /**
     * 在业务事务内锁定存储配置，协调文件上传、清理与配置变更
     * @param exclusive 是否使用独占锁，否则使用共享锁
     * @return 已锁定的配置记录，不存在时为 null
     */
    @Override
    public StorageConfigState getForLock(boolean exclusive) {
        return baseMapper.getForLock(exclusive);
    }
}
