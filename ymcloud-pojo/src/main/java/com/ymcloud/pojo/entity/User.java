package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="用户表")
public class User {
    @Schema(description="用户ID")
    @TableId(type = IdType.AUTO)
    private Long id;
    @Schema(description="登录用户名（唯一）")
    private String username;
    @Schema(description="密码")
    private String password;
    @Schema(description="昵称")
    private String nickname;
    @Schema(description="性别：0=保密，1=男，2=女")
    private Integer sex;
    @Schema(description="邮箱")
    private String email;
    @Schema(description="手机号")
    private String phoneNumber;
    @Schema(description="头像 URL")
    private String avatar;
    @Schema(description="学校")
    private String school;
    @Schema(description="个性签名")
    private String signature;
    @Schema(description="博客链接")
    private String blog;
    @Schema(description="最后登录 IP")
    private String loginIp;
    @Schema(description="最后登录时间")
    private LocalDateTime loginDate;
    @Schema(description="账号状态：1=正常，0=停用，2=删除")
    private Integer status;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
