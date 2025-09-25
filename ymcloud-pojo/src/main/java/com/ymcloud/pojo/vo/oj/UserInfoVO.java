package com.ymcloud.pojo.vo.oj;

import com.ymcloud.pojo.enums.UserSex;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: UserInfoVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  12:38
 * @Description: 用户完整信息实体
 */
@Data
public class UserInfoVO implements Serializable {
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
     * 最后登录时间
     */
    private LocalDateTime loginDate;
    /**
     * 已解决题目的题号
     */
    private List<String> problemId;
    /**
     * 已解决题目个数
     */
    private int ac;
    /**
     * 总提交个数
     */
    private int total;

}
