package com.ymcloud.judge.domain;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: JudgeResultMessage
 * @Author: Yemiao
 * @CreateTime: 2025-08-10  01:23
 * @Description: 评测结果队列消息
 */
@Data
public class JudgeResultMessage {
    /**
     * 提交文件路径
     */
    private String submissionDir;
    /**
     * 正式评测ID
     */
    private Long submissionId;
    /**
     * 评测策略：0=默认评测，1=自测
     */
    private Integer strategy;
    /**
     * 题目ID
     */
    private Long problemId;
    /**
     * 题目展示ID
     */
    private String displayId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 用户名
     */
    private String username;
    /**
     * 评测状态
     */
    private JudgeStatus status;
}
