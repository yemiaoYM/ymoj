package com.ymcloud.pojo.dto.admin;

import com.ymcloud.pojo.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto.admin
 * @ClassName: UpdateUserStatusDTO
 * @Author: Yemiao
 * @CreateTime: 2025-09-14  13:40
 * @Description: 更新用户状态请求体
 */
@Data
public class ResetUserPasswordDTO {
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正整数")
    private Long id;
    /**
     * 密码
     */
    @NotBlank(message = "密码不为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String password;
}
