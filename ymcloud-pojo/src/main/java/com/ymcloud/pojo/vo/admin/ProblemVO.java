package com.ymcloud.pojo.vo.admin;

import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-10-01  22:30
 * @Description: 题目VO
 */
@Data
public class ProblemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 题目ID
     */
    private Long id;
    /**
     * 问题的自定义ID，可作为唯一标识，例如YMOJ-1000（本题库），P1005（洛谷）等等
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
     * 时间限制（ms）
     */
    private Long timeLimit;
    /**
     * 内存限制（KB）
     */
    private Long memoryLimit;
    /**
     * 输入描述
     */
    private String input;
    /**
     * 输出描述
     */
    private String output;
    /**
     * 题目来源说明，最多255字符，可为空；不参与判题和题号生成
     */
    private String source;
    /**
     * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师
     */
    private Integer difficulty;
    /**
     * 判题方式：default=普通、spj=特殊、interactive=交互；当前仅支持 default
     */
    private String judgeMode;
    /**
     * 可见性：1=公开，2=私有，3=比赛专用
     */
    private Integer visibility;
    /**
     * 是否允许查看测试样例结果（0=否，1=是）
     */
    private Boolean openCaseResult;
    /**
     * 题面样例
     */
    private List<ProblemCaseVO> problemCaseList;
    /**
     * 标签列表
     */
    private List<Long> tagList;
}
