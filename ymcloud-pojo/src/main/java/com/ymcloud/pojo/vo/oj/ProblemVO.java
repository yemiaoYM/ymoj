package com.ymcloud.pojo.vo.oj;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.JudgeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ProblemVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  12:20
 * @Description: 前端展示题目信息
 */
@Data
public class ProblemVO implements Serializable {
    private static final long serialVersionUID = 1L;

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
     * 题目来源
     */
    private String source;
    /**
     * 难度：0=未评定，1=入门，2=普及，3=提高，4=省选及以上
     */
    private Integer difficulty;
    /**
     * 评测模式：0=ACM，1=OI
     */
    private Integer mode;
    /**
     * 时间限制（ms）
     */
    private Long timeLimit;
    /**
     * 内存限制（KB）
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
