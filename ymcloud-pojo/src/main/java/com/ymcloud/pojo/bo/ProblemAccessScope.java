package com.ymcloud.pojo.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 服务端生成的题目查询范围，禁止从 HTTP 请求绑定授权字段
 */
@Data
public class ProblemAccessScope {

    /**
     * 当前用户 ID，匿名查询为空
     */
    private Long userId;

    /**
     * 是否具有全站题目管理权限
     */
    private boolean administrator;

    /**
     * 是否可访问普通私有题，不能绕过他人比赛专属题的访问限制
     */
    private boolean privateAccess;

    /**
     * 本次查询统一使用的比赛生命周期判断时刻
     */
    private LocalDateTime now;
}
