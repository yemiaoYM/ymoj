package com.ymcloud.pojo.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: UserVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  13:18
 * @Description: 返回前端用户基本信息
 */
@Data
public class UserVO implements Serializable {
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
    private Integer sex;
    /**
     * 电子游戏
     */
    private String email;
    /**
     * 电话号
     */
    private String phoneNumber;
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
}
