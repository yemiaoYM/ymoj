package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.StorageConfigState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置状态持久映射及配置变更锁
 */
@Mapper
public interface StorageConfigStateMapper extends BaseMapper<StorageConfigState> {

    /**
     * 在业务事务内锁定存储配置，协调文件上传、清理与配置变更
     * @param exclusive 是否使用独占锁，否则使用共享锁
     * @return 已锁定的配置记录，不存在时为 null
     */
    StorageConfigState getForLock(@Param("exclusive") boolean exclusive);
}
