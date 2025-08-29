package com.ymcloud.judge.bo;

import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.util.List;

/**
 * @PackageName: com.ymcloud.judge.bo
 * @ClassName: JudgeResult
 * @Author: Yemiao
 * @CreateTime: 2025-08-29  17:11
 * @Description: 评测结果
 */
@Data
public class JudgeResult {
    /**
     * 评测id
     */
    private Long submissionId;
    /**
     * 评测结果
     */
    private JudgeStatus status;
    /**
     * 运行时间
     */
    private Long time;
    /**
     * 运行内存
     */
    private Long memory;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 测试样例结果
     */
    private List<TestCaseResult> testCaseResults;
}
