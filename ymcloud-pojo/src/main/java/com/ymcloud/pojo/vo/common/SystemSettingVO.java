package com.ymcloud.pojo.vo.common;

import lombok.Data;

/**
 * 系统配置
 */
@Data
public class SystemSettingVO {

    /**
     * 是否允许新用户注册
     */
    private Boolean registerEnabled;
}
