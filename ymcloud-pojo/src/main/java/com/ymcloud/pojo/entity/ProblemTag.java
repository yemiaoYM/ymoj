package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 题目标签关联表。 */
@Data
public class ProblemTag {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题目ID */
    private Long problemId;

    /** 标签ID */
    private Long tagId;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
