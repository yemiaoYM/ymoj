package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.StorageConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置持久映射及上传共享锁
 */
@Mapper
public interface StorageConfigMapper extends BaseMapper<StorageConfig> {

    /**
     * 在业务事务内锁定存储配置，协调文件上传、清理与配置变更
     * @param key 存储配置名
     * @return 已锁定的配置记录，不存在时为 null
     */
    StorageConfig getByIdForShare(@Param("key") String key);
}
