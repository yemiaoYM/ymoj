package com.ymcloud.web.service.common.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ymcloud.common.constant.SystemSettingConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.pojo.dto.admin.SystemSettingDTO;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.pojo.vo.common.SystemSettingVO;
import com.ymcloud.web.service.common.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统配置服务实现类
 */
@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    @Autowired
    private SystemSettingEntityService systemSettingEntityService;

    @Override
    public SystemSettingVO getSystemSetting() {
        SystemSettingVO systemSettingVO = new SystemSettingVO();
        systemSettingVO.setRegisterEnabled(isRegisterEnabled());
        return systemSettingVO;
    }

    @Override
    public boolean isRegisterEnabled() {
        //实时读取数据库，保证保存后所有服务实例立即使用最新配置
        SystemSetting setting = systemSettingEntityService.getOne(Wrappers.<SystemSetting>lambdaQuery()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.REGISTER_ENABLED));
        if (setting == null) {
            throw new ServiceException("注册配置未初始化，请联系管理员");
        }
        String value = setting.getSettingValue();
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new ServiceException("注册配置异常，请联系管理员");
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSystemSetting(SystemSettingDTO systemSettingDTO) {
        if (systemSettingDTO == null || systemSettingDTO.getRegisterEnabled() == null) {
            throw new ServiceException("请选择是否允许注册");
        }
        boolean success = systemSettingEntityService.update(Wrappers.<SystemSetting>lambdaUpdate()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.REGISTER_ENABLED)
                .set(SystemSetting::getSettingValue, systemSettingDTO.getRegisterEnabled().toString()));
        if (!success) {
            throw new ServiceException("保存系统配置失败，请稍后再试");
        }
    }
}
