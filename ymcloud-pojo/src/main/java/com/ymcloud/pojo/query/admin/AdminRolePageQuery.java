package com.ymcloud.pojo.query.admin;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端角色分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminRolePageQuery extends PageQuery {

    /**
     * 角色名称
     */
    @Size(max = 64, message = "角色名称不能超过64个字符")
    private String name;

    /**
     * 角色编码
     */
    @Size(max = 64, message = "角色编码不能超过64个字符")
    private String code;

    /**
     * 状态：0停用，1启用
     */
    @Min(value = 0, message = "角色状态无效")
    @Max(value = 1, message = "角色状态无效")
    private Integer status;
}
