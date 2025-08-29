package com.ymcloud.pojo.bo;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestSubmissionRecord
 * @Author: Yemiao
 * @CreateTime: 2025-08-18  20:23
 * @Description: 比赛提交记录
 */
@Data
public class ContestSubmissionRecord {
    /**
     * 用户名
     */
    private String username;
    /**
     * 学校
     */
    private String school;
    /**
     * 题目展示id
     */
    private String displayId;
    /**
     * 评测状态
     */
    private JudgeStatus status;
    /**
     * 分数
     */
    private Integer score;
    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
}
