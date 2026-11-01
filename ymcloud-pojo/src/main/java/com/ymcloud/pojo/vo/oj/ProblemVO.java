package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  12:20
 * @Description: 前端展示题目信息
 */
@Data
public class ProblemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 比赛正式提交开始时间
     * 普通练习为 null
     */
    private LocalDateTime submissionStartTime;
    /**
     * 比赛正式提交结束时间，不含结束时刻
     * 普通练习为 null
     */
    private LocalDateTime submissionEndTime;
    /**
     * 服务端当前时间，用于比赛页面校准时段
     * 普通练习为 null
     */
    private LocalDateTime serverTime;

    /**
     * 题目展示id 例如YMOJ-1000（本题库），P1005（洛谷）等等
     */
    private String problemId;
    /**
     * 题目标题
     */
    private String title;
    /**
     * 题目描述内容（题干）
     */
    private String content;
    /**
     * 输入描述
     */
    private String input;
    /**
     * 输出描述
     */
    private String output;
    /**
     * 题目来源说明，最多255字符，可为空
     * 不参与判题和题号生成
     */
    private String source;
    /**
     * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
     */
    private Integer difficulty;
    /**
     * 判题方式：default=普通、spj=特殊、interactive=交互
     * 当前仅支持 default
     */
    private String judgeMode;
    /**
     * 时间限制（ms）
     */
    private Long timeLimit;
    /**
     * 内存限制（KiB）
     */
    private Long memoryLimit;
    /**
     * 出题人用户名
     */
    private String authorUsername;
    /**
     * 题面样例
     */
    private List<ProblemCaseVO> problemCaseList;
    /**
     * 编程语言选项
     */
    private List<LanguageVO> languageList;
    /**
     * 用户该题目的通过状态
     */
    private boolean isAccepted;
}
