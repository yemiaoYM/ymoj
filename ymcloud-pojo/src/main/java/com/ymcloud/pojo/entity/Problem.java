package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-30
 * @Description: 题目表；来源记录原题出处，题目统一由本站判题。
 */
@Data
public class Problem {

    /** 题目ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问题的自定义ID，可作为唯一标识，例如YMOJ-1000（本题库），P1005（洛谷）等等 */
    private String problemId;

    /** 题目标题 */
    private String title;

    /** 题目描述内容（题干） */
    private String content;

    /** 时间限制（ms） */
    private Long timeLimit;

    /** 内存限制（KiB） */
    private Long memoryLimit;

    /** 输入描述 */
    @TableField("`input`")
    private String input;

    /** 输出描述 */
    @TableField("`output`")
    private String output;

    /** 题面样例 */
    private String examples;

    /** 题目来源说明，最多255字符，可为空；不参与判题和题号生成 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String source;

    /** 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师 */
    private Integer difficulty;

    /** 判题方式：default=普通、spj=特殊、interactive=交互；当前仅支持 default */
    private String judgeMode;

    /** 可见性：1=公开，2=私有，3=比赛专用 */
    private Integer visibility;

    /** 是否允许查看测试样例结果（0=否，1=是） */
    private Boolean openCaseResult;

    /** 当前不可变测试数据版本ID */
    private Long testDataVersionId;

    /** 出题人ID（user.id） */
    private Long authorId;

    /** 出题人用户名 */
    private String authorUsername;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
