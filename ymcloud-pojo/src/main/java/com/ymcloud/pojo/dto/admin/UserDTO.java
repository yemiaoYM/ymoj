package com.ymcloud.pojo.dto.admin;

import com.ymcloud.pojo.enums.UserSex;
import com.ymcloud.pojo.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.dto.admin
 * @ClassName: UserDTO
 * @Author: Yemiao
 * @CreateTime: 2025-09-14  17:41
 * @Description: 管理端用户DTO
 */
@Data
public class UserDTO {

    /**
     * 用户id
     */
    private Long id;
    /**
     * 登录用户名（唯一）
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度在2到20之间")
    private String username;
    /**
     * 密码
     */
    @Size(min = 5, max = 20, message = "密码长度在5到20之间")
    private String password;
    /**
     * 昵称
     */
    @Size(min = 2, max = 20, message = "昵称长度在2到20之间")
    private String nickname;
    /**
     * 性别：0=保密，1=男，2=女
     */
    private UserSex sex = UserSex.UNKNOWN;
    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email
    private String email;
    /**
     * 头像 URL
     */
    private String avatar;
    /**
     * 学校
     */
    @Size(min = 2, max = 50, message = "学校名称长度在2到50之间")
    private String school;
    /**
     * 个性签名
     */
    @Size(max = 65535, message = "个性签名内容过长")
    private String signature;
    /**
     * 博客链接
     */
    @Size(max = 512, message = "博客链接长度不能超过512")
    private String blog;
    /**
     * 账号状态：1=正常，0=停用
     */
    private UserStatus status = UserStatus.OK;
}
