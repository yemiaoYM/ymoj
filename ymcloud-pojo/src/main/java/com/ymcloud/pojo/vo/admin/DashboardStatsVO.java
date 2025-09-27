package com.ymcloud.pojo.vo.admin;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: DashboardStatsVO
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  20:30
 * @Description: 管理员首页统计数据VO
 */
@Data
public class DashboardStatsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 总用户数
     */
    private Long totalUsers;

    /**
     * 题目总数
     */
    private Long totalProblems;

    /**
     * 比赛总数
     */
    private Long totalContests;
}
