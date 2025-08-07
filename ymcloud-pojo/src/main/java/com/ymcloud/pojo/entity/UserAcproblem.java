package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户ac题目表")
public class UserAcproblem {
    @TableId(type = IdType.AUTO)
    @Schema(description="主键ID")
    private Long id;
    @Schema(description="judge提交ID")
    private Long judgeId;
    @Schema(description="用户ID")
    private Long userId;
    @Schema(description="用户名")
    private String username;
    @Schema(description="题目ID")
    private Long problemId;
    @Schema(description="题目展示ID，例如 YMOJ-1001")
    private String displayId;
    @Schema(description="如果是比赛中的 AC 题，记录比赛ID，普通为0")
    private Long contestId;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
