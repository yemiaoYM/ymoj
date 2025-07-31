package com.ymcloud.pojo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: UpdateUserEmailDTO
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  16:53
 * @Description: 更新当前用户电子邮箱请求体
 */
@Data
public class UpdateUserEmailDTO {
    /**
     * 当前密码
     */
    @NotBlank(message = "当前密码不能为空")
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String password;
    /**
     * 新邮箱
     */
    @NotBlank(message = "新邮箱不能为空")
    @Email
    private String newEmail;

}
