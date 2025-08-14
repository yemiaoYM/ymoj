package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "比赛报名用户关联表")
public class ContestUser {
    @TableId(type = IdType.AUTO)
    @Schema(description="主键ID")
    private Long id;
    @Schema(description="比赛ID")
    private Long contestId;
    @Schema(description="用户ID")
    private Long userId;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
