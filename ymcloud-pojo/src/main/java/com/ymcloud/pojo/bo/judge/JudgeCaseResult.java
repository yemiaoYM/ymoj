package com.ymcloud.pojo.bo.judge;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;
import lombok.ToString;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 单个测试点的判定、资源用量及有界输出预览
 */
@Data
public class JudgeCaseResult implements JudgeMessageStatus {

    /**
     * 所属提交编号
     */
    private Long judgeId;

    /**
     * 从 1 开始的测试点序号
     */
    private Integer caseIndex;

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
     * 测试点输出的有界预览
     */
    @ToString.Exclude
    private String output;
}
