package com.ymcloud.pojo.dto.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-10
 * @Description: 系统配置修改请求
 */
@Data
public class SystemSettingDTO {

    /**
     * 是否允许新用户注册
     */
    @NotNull(message = "请选择是否允许注册")
    private Boolean registerEnabled;

    /** 是否显示首页备案栏；关闭时仍保留备案号。 */
    @NotNull(message = "请选择是否显示备案栏")
    private Boolean filingEnabled;

    /** ICP 备案号；最多 100 字符，空串表示不展示该项。 */
    @NotNull(message = "请传入ICP备案号，未配置时使用空字符串")
    @Size(max = 100, message = "ICP备案号不能超过100个字符")
    private String icpFilingNumber;

    /** 公安备案号；最多 100 字符，含一段连续数字，空串表示不展示该项。 */
    @NotNull(message = "请传入公安备案号，未配置时使用空字符串")
    @Size(max = 100, message = "公安备案号不能超过100个字符")
    @Pattern(regexp = "(?:\\s*|[^0-9]*[0-9]+[^0-9]*)", message = "公安备案号应包含一段连续的数字编号")
    private String policeFilingNumber;

    /**
     * 检查开启备案栏时是否至少填写一项备案号。
     * @return 关闭备案栏或存在非空白备案号时为 true
     */
    @JsonIgnore
    @AssertTrue(message = "开启备案栏时请至少填写一项备案号")
    public boolean isFilingConfigured() {
        return !Boolean.TRUE.equals(filingEnabled)
                || (icpFilingNumber != null && !icpFilingNumber.isBlank())
                || (policeFilingNumber != null && !policeFilingNumber.isBlank());
    }
}
