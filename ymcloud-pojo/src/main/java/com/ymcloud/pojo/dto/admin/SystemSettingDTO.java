package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 系统配置修改请求
 */
@Data
public class SystemSettingDTO {

    /**
     * 是否允许新用户注册
     */
    @NotNull(message = "请选择是否允许注册")
    private Boolean registerEnabled;
}
