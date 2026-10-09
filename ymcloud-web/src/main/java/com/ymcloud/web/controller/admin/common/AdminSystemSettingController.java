package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.SystemSettingDTO;
import com.ymcloud.pojo.vo.common.SystemSettingVO;
import com.ymcloud.web.service.common.SystemSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置管理
 */
@Slf4j
@RestController
@RequestMapping("/admin/system-setting")
public class AdminSystemSettingController {

    @Autowired
    private SystemSettingService systemSettingService;

    /**
     * 查询系统配置
     * @return 系统配置
     */
    @PreAuthorize("hasAuthority('system:setting:list')")
    @GetMapping
    public Result<SystemSettingVO> getSystemSetting() {
        log.info("查询系统配置");
        return Result.ok(systemSettingService.getSystemSetting());
    }

    /**
     * 修改系统配置
     * @param systemSettingDTO 系统配置
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('system:setting:edit')")
    @PutMapping
    public Result<Void> updateSystemSetting(@Validated @RequestBody SystemSettingDTO systemSettingDTO) {
        log.info("修改系统配置:{}", systemSettingDTO);
        systemSettingService.updateSystemSetting(systemSettingDTO);
        return Result.ok();
    }
}
