package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 标签分类表。 */
@Data
public class TagClassification {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签分类名称 */
    private String name;

    /** 分类优先级（数值越小优先级越高） */
    private Integer priority;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
