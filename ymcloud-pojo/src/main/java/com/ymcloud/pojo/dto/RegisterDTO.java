package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: RegisterDTO
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  11:43
 * @Description: 注册请求体
 */
@Data
public class RegisterDTO {
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度在2到20之间")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String password;

    /**
     * 电子邮箱
     */
    @NotBlank(message = "电子邮箱不能为空")
    @Email(message = "电子邮箱格式不正确，请填写正确的邮箱")
    private String email;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 验证码唯一标识
     */
    @NotBlank(message = "非法登录")
    private String uuid;
}
