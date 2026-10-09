package com.ymcloud.web.service.common;

import com.ymcloud.pojo.dto.admin.SystemSettingDTO;
import com.ymcloud.pojo.vo.common.SystemSettingVO;

/**
 * 系统配置服务
 */
public interface SystemSettingService {

    /**
     * 查询系统配置
     * @return 系统配置
     */
    SystemSettingVO getSystemSetting();

    /**
     * 判断是否允许新用户注册
     * @return 注册开关
     */
    boolean isRegisterEnabled();

    /**
     * 修改系统配置
     * @param systemSettingDTO 系统配置
     */
    void updateSystemSetting(SystemSettingDTO systemSettingDTO);
}
