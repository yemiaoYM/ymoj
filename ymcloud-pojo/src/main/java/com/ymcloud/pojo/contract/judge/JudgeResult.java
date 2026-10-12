package com.ymcloud.pojo.contract.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * 带执行标识的进度或最终结果。sequence 为 1 编译、2 运行、3 最终，资源单位与任务一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResult {

    @Builder.Default
    private int schemaVersion = JudgeProtocol.SCHEMA_VERSION;

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
