package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户角色分配
 */
@Data
public class UserRoleDTO {

    /**
     * 角色ID列表，空列表表示清空角色
     */
    @NotNull(message = "角色列表不能为空")
    @Size(max = 100, message = "一次最多分配100个角色")
    private List<@NotNull @Positive Long> roleIds;
}
