package com.ymcloud.web.service.common.impl;

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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-10
 * @Description: 读取网站配置并在事务内保存开关与备案信息
 */
@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    @Autowired
    private SystemSettingEntityService systemSettingEntityService;

    /** {@inheritDoc} */
    @Override
    public SystemSettingVO getSystemSetting() {
        SystemSettingVO systemSettingVO = new SystemSettingVO();
        systemSettingVO.setRegisterEnabled(isRegisterEnabled());
        Map<String, String> filingSettings = systemSettingEntityService.lambdaQuery()
                .in(SystemSetting::getSettingKey, List.of(SystemSettingConstants.FILING_ENABLED,
                        SystemSettingConstants.ICP_FILING_NUMBER, SystemSettingConstants.POLICE_FILING_NUMBER))
                .list()
                .stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, SystemSetting::getSettingValue));
        // 旧数据库没有这些键时保持关闭，不展示源码作者的备案信息。
        systemSettingVO.setFilingEnabled("true".equals(filingSettings.get(SystemSettingConstants.FILING_ENABLED)));
        systemSettingVO.setIcpFilingNumber(filingSettings.getOrDefault(SystemSettingConstants.ICP_FILING_NUMBER, ""));
        systemSettingVO.setPoliceFilingNumber(filingSettings.getOrDefault(SystemSettingConstants.POLICE_FILING_NUMBER, ""));
        return systemSettingVO;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRegisterEnabled() {
        //实时读取数据库，保证保存后所有服务实例立即使用最新配置
        SystemSetting setting = systemSettingEntityService.lambdaQuery()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.REGISTER_ENABLED)
                .one();
        if (setting == null) {
            throw new ServiceException("注册配置未初始化，请联系管理员");
        }
        String value = setting.getSettingValue();
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new ServiceException("注册配置异常，请联系管理员");
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 保存网站设置；注册配置行的更新锁串行化首次补齐备案配置的操作。
     * @param systemSettingDTO 已通过 HTTP 结构校验的网站配置
     * @throws ServiceException 注册配置缺失或任一配置写入失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSystemSetting(SystemSettingDTO systemSettingDTO) {
        if (systemSettingDTO == null || systemSettingDTO.getRegisterEnabled() == null) {
            throw new ServiceException("请选择是否允许注册");
        }
        boolean success = systemSettingEntityService.lambdaUpdate()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.REGISTER_ENABLED)
                .set(SystemSetting::getSettingValue, systemSettingDTO.getRegisterEnabled().toString())
                .update();
        if (!success) {
            throw new ServiceException("保存系统配置失败，请稍后再试");
        }
        saveSetting(SystemSettingConstants.FILING_ENABLED, systemSettingDTO.getFilingEnabled().toString());
        saveSetting(SystemSettingConstants.ICP_FILING_NUMBER, systemSettingDTO.getIcpFilingNumber().strip());
        saveSetting(SystemSettingConstants.POLICE_FILING_NUMBER, systemSettingDTO.getPoliceFilingNumber().strip());
    }

    /**
     * 保存一个备案配置键，旧数据库在首次保存时补齐；仅在持有注册配置行锁的事务内调用。
     * @param key 服务端定义的配置键
     * @param value 已校验且非 null 的配置值
     * @throws ServiceException 写入失败，交由外层事务回滚整次保存
     */
    private void saveSetting(String key, String value) {
        SystemSetting setting = systemSettingEntityService.lambdaQuery()
                .eq(SystemSetting::getSettingKey, key)
                .one();
        boolean success;
        if (setting == null) {
            setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            success = systemSettingEntityService.save(setting);
        } else {
            success = systemSettingEntityService.lambdaUpdate()
                    .eq(SystemSetting::getSettingKey, key)
                    .set(SystemSetting::getSettingValue, value)
                    .update();
        }
        if (!success) {
            throw new ServiceException("保存系统配置失败，请稍后再试");
        }
    }
}
