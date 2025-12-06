package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo.oj
 * @ClassName: ContestSubmissionInfo
 * @Author: Yemiao
 * @CreateTime: 2025-12-02  22:08
 * @Description: 比赛用户提交信息，用于比赛排名模块
 */
@Data
public class ContestSubmissionInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 错误数
     */
    private Integer errorNum;
    /**
     * 是否通过
     */
    private Integer isAC;
    /**
     * ac的时间=提交时间-比赛开始时间
     */
    private Long ACTime;
    /**
     * 是否为一血
     */
    private Integer isFirstAC;
    /**
     * 本题获取的最高分数
     */
    private Integer score;
}
