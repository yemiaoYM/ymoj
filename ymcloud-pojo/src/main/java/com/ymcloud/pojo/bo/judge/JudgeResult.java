package com.ymcloud.pojo.bo.judge;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 带执行标识的编译、运行进度或最终结果，资源单位与任务一致
 */
@Data
public class JudgeResult implements JudgeMessageStatus {

    /**
     * 本次判题执行的唯一标识，重试沿用原值
     */
    private String executionId;

    /**
     * 正式提交或自测的提交编号
     */
    private Long submissionId;

    /**
     * 题目内部主键，自测允许为空
     */
    private Long problemId;

    /**
     * 评测策略，0 为正式提交，1 为自测
     */
    private Integer strategy;

    /**
     * 结果所属 Worker 标识
     */
    private String workerId;

    /**
     * 本次消息是否结束该次执行
     */
    private boolean terminal;

    /**
     * 执行阶段序号，1 为编译，2 为评测，3 为终态
     */
    private int sequence;

    /**
     * 结果产生时间，单位为 UTC epoch 毫秒
     */
    private Long emittedAtEpochMs;

    /**
     * 判题状态，消息使用显式数字编号
     */
    private JudgeStatus status;

    /**
     * 运行耗时，单位为毫秒
     */
    private Long time;

    /**
     * 内存用量，单位为 KiB
     */
    private Long memory;

    /**
     * 判题得分，范围为 0 至 100
     */
    private Integer score;

    /**
     * 有界诊断信息，可能包含私有内容
     */
    @ToString.Exclude
    private String errorMessage;

    /**
     * 按执行顺序记录的逐点结果和有界预览
     */
    @ToString.Exclude
    private List<JudgeCaseResult> testCaseResults;
}
