package com.ymcloud.pojo.vo;

import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: SubmissionVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-04  12:58
 * @Description: 提交详情
 */
@Data
public class SubmissionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 提交id
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
     * 用户提交代码（个人或者允许分享查看）
     */
    private String code;
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
     * 编译错误/运行时错误信息
     */
    private String errorMessage;
    /**
     * 是否公开代码：0=私有，1=公开
     */
    private Integer share;
    /**
     * 执行判题的机器标识
     */
    private String judgeServer;
    /**
     * 提交时间
     */
    private LocalDateTime createdTime;
    /**
     * 各个评测点结果
     */
    private List<JudgeCaseVO> judgeCases;

}
