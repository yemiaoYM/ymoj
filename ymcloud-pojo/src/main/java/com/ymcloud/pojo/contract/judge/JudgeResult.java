package com.ymcloud.pojo.contract.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 带执行标识的编译、运行进度或最终结果，资源单位与任务一致
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResult {

    private String executionId;
    private Long submissionId;
    private Long problemId;
    private Long testDataVersionId;
    private Integer strategy;
    private String workerId;

    private boolean terminal;
    private int sequence;
    private Long emittedAtEpochMs;

    private JudgeVerdict status;
    private Long time;
    private Long memory;
    private Integer score;

    @ToString.Exclude
    private String errorMessage;

    @ToString.Exclude
    private List<TestCaseResult> testCaseResults;
}
