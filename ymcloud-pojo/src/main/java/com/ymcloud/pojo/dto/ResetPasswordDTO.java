package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: ResetPasswordDTO
 * @Author: Yemiao
 * @CreateTime: 2025-08-17  18:05
 * @Description: 重置密码请求体
 */
@Data
public class ResetPasswordDTO {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度在2到20之间")
    private String username;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String password;

    /**
     * 确认密码
     */
    @NotBlank(message = "确认密码不为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String rePassword;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 验证码唯一标识
     */
    @NotBlank(message = "非法请求")
    private String uuid;

}
