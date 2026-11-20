package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.StorageConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 存储配置持久映射及上传共享锁
 */
@Mapper
public interface StorageConfigMapper extends BaseMapper<StorageConfig> {

    /**
     * 读取并锁定当前默认配置，保证上传登记期间存储位置稳定
     * @return 已锁定的配置记录，不存在时为 null
     */
    StorageConfig getDefaultForShare();
}
