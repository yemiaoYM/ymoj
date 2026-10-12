package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户ac题目表。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserAcproblem {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交ID */
    private Long submissionId;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 题目ID */
    private Long problemId;

    /** 题目展示ID，例如 YMOJ-1001 */
    private String displayId;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
