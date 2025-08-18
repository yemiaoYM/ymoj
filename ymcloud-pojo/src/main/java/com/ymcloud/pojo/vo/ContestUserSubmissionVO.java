package com.ymcloud.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestUserSubmissionVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-18  18:52
 * @Description: 比赛用户提交信息，用于比赛排名模块
 */
@Data
public class ContestUserSubmissionVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 题目获得的最高分
     */
    private int score;
    /**
     * 错误数
     */
    private int errorNum;
    /**
     * 是否通过
     */
    private boolean isAc;
    /**
     * ac的时间=提交时间-比赛开始时间
     */
    private long acTime;
    /**
     * 是否为一血(暂不支持)
     */
    private boolean isFirstAc;
}
