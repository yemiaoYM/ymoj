package com.ymcloud.pojo.vo.admin;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: UserOnlineVO
 * @Author: Yemiao
 * @CreateTime: 2025-09-14  21:34
 * @Description: 在线用户前端实体
 */
@Data
public class UserOnlineVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * token
     */
    private String token;
    /**
     * 用户名
     */
    private String username;
    /**
     * IP地址
     */
    private String ipaddr;
    /**
     * 登录地点
     */
    private String loginLocation;
    /**
     * 浏览器
     */
    private String browser;
    /**
     * 操作系统
     */
    private String os;
    /**
     * 登录时间
     */
    private LocalDateTime loginDateTime;
}
