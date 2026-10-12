package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.UserSex;
import com.ymcloud.pojo.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户表。 */
@Data
public class User {

    /** 用户ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** 密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 性别：0=保密，1=男，2=女 */
    private UserSex sex;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 学校 */
    private String school;

    /** 个性签名 */
    private String signature;

    /** 博客链接 */
    private String blog;

    /** 最后登录 IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

    /** 账号状态：1=正常，0=停用 */
    private UserStatus status;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
