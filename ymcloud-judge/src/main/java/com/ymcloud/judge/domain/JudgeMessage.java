package com.ymcloud.judge.domain;

import com.ymcloud.pojo.entity.Language;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: JudgeMessage
 * @Author: Yemiao
 * @CreateTime: 2025-08-03  17:19
 * @Description: 评测队列消息
 */
@Data
public class JudgeMessage {

    /**
     * 评测id
     */
    private Long submissionId;
    /**
     * 题目id
     */
    private Long problemId;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 用户代码
     */
    private String code;
    /**
     * 评测语言
     */
    private Language language;
    /**
     * 时间限制ms
     */
    private Long timeLimit;
    /**
     * 内存限制kb
     */
    private Long memoryLimit;


}
