package com.ymcloud.pojo.query.admin;

import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query.admin
 * @ClassName: AdminUserPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:57
 * @Description: 管理端用户条件分页查询
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AdminUserPageQuery extends PageQuery {

    /**
     * 用户名
     */
    @Size(max = 20, message = "用户名长度不能超过20")
    private String username;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 状态
     */
    private Integer status;

}
