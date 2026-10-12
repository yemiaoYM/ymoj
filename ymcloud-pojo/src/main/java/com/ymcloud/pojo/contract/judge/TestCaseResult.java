package com.ymcloud.pojo.contract.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 单个测试点结果。输出只保存有界预览，不包含私有输入或预期输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseResult {

    private Long judgeId;
    private Integer caseIndex;
    private JudgeVerdict status;
    private Long time;
    private Long memory;
    private Integer score;

    @ToString.Exclude
    private String errorMessage;

    @ToString.Exclude
    private String output;
}
