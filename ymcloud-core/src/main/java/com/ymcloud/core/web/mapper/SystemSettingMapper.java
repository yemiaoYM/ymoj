package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.SystemSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置数据访问
 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {
}
