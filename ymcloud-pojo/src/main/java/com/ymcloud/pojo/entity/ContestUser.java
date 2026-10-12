package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 比赛报名用户关联表。 */
@Data
public class ContestUser {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 比赛ID */
    private Long contestId;

    /** 用户ID */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
