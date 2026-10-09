package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色配置
 */
@Data
public class RoleDTO {

    /**
     * 角色编码
     */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码不能超过64个字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码只能包含大写字母、数字和下划线")
    private String code;

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称不能超过64个字符")
    private String name;

    /**
     * 角色描述
     */
    @Size(max = 255, message = "角色描述不能超过255个字符")
    private String description;

    /**
     * 状态：0停用，1启用
     */
    @NotNull(message = "请选择角色状态")
    @Min(value = 0, message = "角色状态无效")
    @Max(value = 1, message = "角色状态无效")
    private Integer status;
}
