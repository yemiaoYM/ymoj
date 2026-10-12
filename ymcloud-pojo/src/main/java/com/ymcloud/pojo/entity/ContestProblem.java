package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 比赛题目关联表。 */
@Data
public class ContestProblem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 该题目在比赛中的顺序id */
    private String displayId;

    /** 比赛id */
    private Long contestId;

    /** 题目id */
    private Long problemId;

    /** 该题目在比赛中的标题，默认为原名字 */
    private String displayTitle;

    /** 气球颜色 */
    private String color;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
