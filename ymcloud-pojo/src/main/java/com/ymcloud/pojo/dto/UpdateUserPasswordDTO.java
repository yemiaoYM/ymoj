package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: UpdateUserPasswordDTO
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  15:56
 * @Description: 更新当前用户密码请求体
 */
@Data
public class UpdateUserPasswordDTO {
    /**
     * 当前密码
     */
    @NotBlank(message = "密码不为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String password;
    /**
     * 新密码
     */
    @NotBlank(message = "新密码不为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String newPassword;
    /**
     * 确认密码
     */
    @NotBlank(message = "确认密码不为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String rePassword;
}
