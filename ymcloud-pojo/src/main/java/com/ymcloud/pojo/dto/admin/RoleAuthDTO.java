package com.ymcloud.pojo.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色权限分配
 */
@Data
public class RoleAuthDTO {

    /**
     * 权限ID列表，空列表表示清空授权
     */
    @NotNull(message = "权限列表不能为空")
    @Size(max = 1000, message = "一次最多分配1000项权限")
    private List<@NotNull @Positive Long> authIds;
}
