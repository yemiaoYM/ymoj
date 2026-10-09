package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.core.web.mapper.SystemSettingMapper;
import com.ymcloud.pojo.entity.SystemSetting;
import org.springframework.stereotype.Service;

/**
 * 系统配置实体服务实现类
 */
@Service
public class SystemSettingEntityServiceImpl extends ServiceImpl<SystemSettingMapper, SystemSetting>
        implements SystemSettingEntityService {
}
