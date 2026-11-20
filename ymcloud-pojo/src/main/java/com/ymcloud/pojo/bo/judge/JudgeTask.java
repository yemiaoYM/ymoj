package com.ymcloud.pojo.bo.judge;

import lombok.Data;
import lombok.ToString;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 创建任务时固定的评测快照
 * 时间为毫秒，内存为 KiB，时间戳为 UTC epoch 毫秒
 * 代码与自测数据属于用户私有内容，不参与日志字符串
 */
@Data
public class JudgeTask {

    /**
     * 每次评测独立生成，重试保持相同标识
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
     * 提交时间，单位为 UTC epoch 毫秒
     */
    private Long submittedAtEpochMs;

    /**
     * 本次执行的总截止时间，单位为 UTC epoch 毫秒
     */
    private Long deadlineEpochMs;

    /**
     * 0：正式提交，1：自测
     */
    private Integer strategy;

    /**
     * 0：ACM，1：OI
     */
    private Integer mode;

    /**
     * 用户提交的源码
     */
    @ToString.Exclude
    private String code;

    /**
     * 自测输入，允许空字符串
     */
    @ToString.Exclude
    private String input;

    /**
     * 自测预期输出，允许空字符串
     */
    @ToString.Exclude
    private String expectedOutput;

    /**
     * 本站语言名，供 Worker 选择固定工具链
     */
    private String language;

    /**
     * 单点运行时间上限，单位为毫秒
     */
    private Long timeLimit;

    /**
     * 单点内存上限，单位为 KiB
     */
    private Long memoryLimit;

    /**
     * 特殊判题源码快照，null 表示普通输出比较
     * 自测仅使用普通输出比较
     */
    @ToString.Exclude
    private SpjProgram spj;
}
