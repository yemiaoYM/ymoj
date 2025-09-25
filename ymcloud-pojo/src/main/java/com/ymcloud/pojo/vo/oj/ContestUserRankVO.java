package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestUserRankVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-18  18:42
 * @Description: 比赛用户排名实体
 */
@Data
public class ContestUserRankVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 比赛排名（可能并列）
     */
    private Integer rank;
    /**
     * 实际排名
     */
    private Integer order;
    /**
     * 用户名
     */
    private String username;
    /**
     * 学校
     */
    private String school;
    /**
     * 通过数
     */
    private int ac;
    /**
     * 提交总数
     */
    private int total;
    /**
     * 总分数
     */
    private int totalScore;
    /**
     * 总时间=所有AC题目的耗时+罚时
     */
    private long totalTime;
    /**
     * 各个题目的提交情况
     * key为比赛题目的displayId
     */
    private Map<String,ContestUserSubmissionVO> submissionInfo;
}
