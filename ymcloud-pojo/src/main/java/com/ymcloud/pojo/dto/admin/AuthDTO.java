package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单和操作权限配置
 */
@Data
public class AuthDTO {

    /**
     * 权限编码
     */
    @Size(max = 128, message = "权限编码不能超过128个字符")
    @Pattern(regexp = "^[a-zA-Z0-9:_-]*$", message = "权限编码格式错误")
    private String code;

    /**
     * 权限名称
     */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 128, message = "权限名称不能超过128个字符")
    private String name;

    /**
     * 上级权限ID，0表示顶层
     */
    @NotNull(message = "请选择上级权限")
    @Min(value = 0, message = "上级权限无效")
    private Long parentId;

    /**
     * 路由路径
     */
    @Size(max = 255, message = "路由路径不能超过255个字符")
    private String path;

    /**
     * 路由名称
     */
    @Size(max = 128, message = "路由名称不能超过128个字符")
    private String routeName;

    /**
     * 页面组件路径
     */
    @Size(max = 255, message = "组件路径不能超过255个字符")
    private String component;

    /**
     * 菜单图标
     */
    @Size(max = 64, message = "图标名称不能超过64个字符")
    private String icon;

    /**
     * 权限类型：M目录，C页面，F操作
     */
    @NotBlank(message = "请选择权限类型")
    @Pattern(regexp = "^[MCF]$", message = "权限类型无效")
    private String type;

    /**
     * 状态：0停用，1启用
     */
    @NotNull(message = "请选择权限状态")
    @Min(value = 0, message = "权限状态无效")
    @Max(value = 1, message = "权限状态无效")
    private Integer status;
}
