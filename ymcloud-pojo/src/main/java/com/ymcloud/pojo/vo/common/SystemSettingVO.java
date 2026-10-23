package com.ymcloud.pojo.vo.common;

import lombok.Data;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-10
 * @Description: 可公开读取的网站系统配置
 */
@Data
public class SystemSettingVO {

    /**
     * 是否允许新用户注册
     */
    private Boolean registerEnabled;

    /** 是否显示首页备案栏；尚未配置时为 false。 */
    private Boolean filingEnabled;

    /** ICP 备案号；未配置时为空串。 */
    private String icpFilingNumber;

    /** 公安备案号；未配置时为空串。 */
    private String policeFilingNumber;
}
