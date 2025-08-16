package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: ForgetPasswordDTO
 * @Author: Yemiao
 * @CreateTime: 2025-08-17  03:42
 * @Description: 忘记密码请求体
 */
@Data
public class ForgetPasswordDTO {
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度在2到20之间")
    private String username;

    /**
     * 电子邮箱
     */
    @NotBlank(message = "电子邮箱不能为空")
    @Email(message = "电子邮箱格式不正确，请填写正确的邮箱")
    private String email;
}
