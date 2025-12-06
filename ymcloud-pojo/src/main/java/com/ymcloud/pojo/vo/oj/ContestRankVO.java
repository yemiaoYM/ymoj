package com.ymcloud.pojo.vo.oj;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @PackageName: com.ymcloud.pojo.vo.oj
 * @ClassName: ContestRankVO
 * @Author: Yemiao
 * @CreateTime: 2025-12-02  22:05
 * @Description: 比赛用户排名实体
 */
@Data
public class ContestRankVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 比赛排名
     */
    private Integer rank;
    /**
     * 用户名
     */
    private String username;
    /**
     * 昵称
     */
    private String nickname;
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
     * 总时间=所有AC题目的耗时+罚时
     */
    private long totalTime;
    /**
     * 总分数
     */
    private long totalScore;
    /**
     * 各个题目的提交情况
     * key为比赛题目的displayId
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String,ContestSubmissionInfo> submissionInfo;

}
