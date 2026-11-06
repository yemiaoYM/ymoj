package com.ymcloud.pojo.contract.judge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 创建任务时固定的评测快照。时间为毫秒，内存为 KiB，时间戳为 UTC epoch 毫秒。
 * 代码与自测数据属于用户私有内容，不参与日志字符串。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeTask {

    @Builder.Default
    private int schemaVersion = JudgeProtocol.SCHEMA_VERSION;

    /** 每次评测独立生成，重试保持相同标识。 */
    private String executionId;
    private Long submissionId;

    private Long problemId;
    private Long testDataVersionId;
    private String manifestSha256;

    private Long submittedAtEpochMs;
    private Long deadlineEpochMs;

    /** 0：正式提交；1：自测。 */
    private Integer strategy;

    /** 0：ACM；1：OI。 */
    private Integer mode;

    @ToString.Exclude
    private String code;

    @ToString.Exclude
    private String input;

    @ToString.Exclude
    private String expectedOutput;

    private JudgeLanguage languageConfig;
    private Long timeLimit;
    private Long memoryLimit;

    /**
     * 特殊判题源码快照，null 表示普通输出比较
     * 自测仅使用普通输出比较
     */
    @ToString.Exclude
    private SpjProgram spj;
}
