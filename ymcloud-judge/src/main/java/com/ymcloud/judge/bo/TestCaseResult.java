package com.ymcloud.judge.bo;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

/**
 * @PackageName: com.ymcloud.judge.bo
 * @ClassName: TestCaseResult
 * @Author: Yemiao
 * @CreateTime: 2025-08-29  19:28
 * @Description: 测试样例评测结果
 */
@Data
public class TestCaseResult {
    /**
     * 提交ID
     */
    private Long judgeId;
    /**
     * 样例在评测中的顺序编号（从0或1开始）
     */
    private Integer caseIndex;
    /**
     * 评测结果
     */
    private JudgeStatus status;
    /**
     * 运行耗时(ms)
     */
    private Long time;
    /**
     * 运行内存(KB)
     */
    private Long memory;
    /**
     * 该样例得分（OI模式下有效）
     */
    private Integer score;
    /**
     * 运行错误信息（CE、RE、SE等时可能会有）
     */
    private String errorMessage;
    /**
     * 运行输出（部分信息）
     */
    private String output;
}
