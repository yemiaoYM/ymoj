package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-13  01:28
 * @Description: 比赛详情信息
 */
@Data
public class ContestVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 比赛创建者用户名或主办方
     */
    private String creatorUsername;
    /**
     * 比赛标题
     */
    private String title;
    /**
     * 比赛说明内容
     */
    private String description;
    /**
     * 来源比赛ID（0表示原创，其它为克隆赛ID）
     */
    private Long sourceId;
    /**
     * 是否计入Rating积分（0=否，1=是）
     */
    private Integer rated;
    /**
     * 赛制模式：0=ACM，1=OI，2=IOI
     */
    private Integer mode;
    /**
     * 比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
     */
    private Integer style;
    /**
     * 比赛开始时间
     */
    private LocalDateTime startTime;
    /**
     * 比赛结束时间
     */
    private LocalDateTime endTime;
    /**
     * 当前时间
     */
    private LocalDateTime now;
    /**
     * 比赛当前状态
     */
    private String status;
    /**
     * 比赛时长（单位：分钟）
     */
    private Long duration;
    /**
     * 是否允许比赛结束后继续提交（0=否，1=是）
     */
    private Integer allowEndSubmit;
    /**
     * 参与人数
     */
    private Long count;
    /**
     * 当前用户的比赛权限
     */
    private Boolean access;

}
