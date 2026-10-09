package com.ymcloud.pojo.vo.oj;

import com.ymcloud.pojo.enums.UserSex;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: UserVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  13:18
 * @Description: 返回前端用户基本信息
 */
@Data
public class UserProfileVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;
    /**
     * 昵称
     */
    private String nickname;
    /**
     * 性别
     */
    private UserSex sex;
    /**
     * 电子邮箱
     */
    private String email;
    /**
     * 头像
     */
    private String avatar;
    /**
     * 学校
     */
    private String school;
    /**
     * 个性签名
     */
    private String signature;
    /**
     * 博客
     */
    private String blog;
    /**
     * 登录时间
     */
    private LocalDateTime loginDateTime;
    /**
     * IP地址
     */
    private String ipaddr;
    /**
     * 登录地点
     */
    private String loginLocation;
    /**
     * 浏览器类型
     */
    private String browser;
    /**
     * 操作系统
     */
    private String os;
    /**
     * 用户角色信息
     */
    private List<String> roles;

    /**
     * 用户权限信息
     */
    private List<String> permissions;
}
