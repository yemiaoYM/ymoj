package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.StorageConfigState;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置状态实体访问契约
 */
public interface StorageConfigStateEntityService extends IService<StorageConfigState> {

    /**
     * 在业务事务内锁定存储配置，协调文件上传、清理与配置变更
     * @param exclusive 是否使用独占锁，否则使用共享锁
     * @return 已锁定的配置记录，不存在时为 null
     */
    StorageConfigState getForLock(boolean exclusive);
}
