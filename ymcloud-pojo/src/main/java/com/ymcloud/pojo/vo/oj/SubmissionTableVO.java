package com.ymcloud.pojo.vo.oj;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: SubmissionTableVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  16:05
 * @Description: 前端判题提交列表
 */
@Data
public class SubmissionTableVO {
    /**
     * 当前用户是否可重评该提交，功能权限仍由管理入口检查
     */
    private Boolean canRejudge;

    /** 当前执行标识，历史提交为 null，用于管理操作的并发检查 */
    private String executionId;

    /**
     * 提交ID，来自 judge 表
     */
    private Long submissionId;
    /**
     * 题目标题
     */
    private String problemTitle;
    /**
     * 题目展示ID，例如 ABC100-A
     */
    private String displayId;
    /**
     * 用户名
     */
    private String username;
    /**
     * 编程语言名称
     */
    private String language;
    /**
     * 代码长度，单位：字符
     */
    private Integer codeLength;
    /**
     * 评测状态
     */
    private JudgeStatus status;
    /**
     * 得分，ACM 模式为 NULL，OI 模式使用
     */
    private Integer score;
    /**
     * 执行耗时(ms)
     */
    private Long time;
    /**
     * 内存使用(KB)
     */
    private Long memory;
    /**
     * 执行判题的机器标识
     */
    private String judgeServer;
    /**
     * 提交时间
     */
    private LocalDateTime createdTime;
}
