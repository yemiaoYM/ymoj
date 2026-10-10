package com.ymcloud.pojo.bo;

import com.ymcloud.pojo.entity.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @PackageName: com.ymcloud.pojo.bo
 * @ClassName: JudgeTask
 * @Author: Yemiao
 * @CreateTime: 2025-08-29  16:05
 * @Description: 评测任务实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JudgeTask {
    /**
     * 评测id
     */
    private Long submissionId;
    /**
     * 题目id
     */
    private Long problemId;
    /**
     * 提交时固定的测试数据版本，旧队列任务可能为空
     */
    private Long testDataVersionId;
    /**
     * 用户输入
     */
    private String input;
    /**
     * 期望输出
     */
    private String expectedOutput;
    /**
     * 用户代码
     */
    private String code;
    /**
     * 评测语言
     */
    private Language languageConfig;
    /**
     * 时间限制ms
     */
    private Long timeLimit;
    /**
     * 内存限制kb
     */
    private Long memoryLimit;
    /**
     * 评测模式：0=ACM，1=OI
     */
    private Integer mode;
    /**
     * 评测策略：0=默认评测，1=自测
     */
    private Integer strategy;
}
