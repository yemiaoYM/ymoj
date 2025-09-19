package com.ymcloud.pojo.vo;

import com.ymcloud.pojo.enums.UserSex;
import lombok.Data;

import java.io.Serializable;
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
     * 用户角色信息
     */
    private List<String> roles;

    /**
     * 用户权限信息
     */
    private List<String> permissions;
}
