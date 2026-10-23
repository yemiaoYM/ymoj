package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.ymcloud.pojo.enums.ProblemSource;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: YeMiaoo
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
    private String input;

    /** 输出描述 */
    private String output;

    /** 题面样例 */
    private String examples;

    /** 题目来源 */
    private ProblemSource source;

    /** 难度：0=未评定，1=入门，2=普及，3=提高，4=省选及以上 */
    private Integer difficulty;

    /** 评测模式：0=ACM，1=OI */
    private Integer mode;

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
