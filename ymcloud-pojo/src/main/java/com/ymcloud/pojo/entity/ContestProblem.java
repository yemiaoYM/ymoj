package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "比赛题目关联表")
public class ContestProblem {
    @TableId(type = IdType.AUTO)
    @Schema(description="主键")
    private Long id;
    @Schema(description="该题目在比赛中的顺序id")
    private String displayId;
    @Schema(description="比赛id")
    private Long contestId;
    @Schema(description="题目id")
    private Long problemId;
    @Schema(description="该题目在比赛中的标题，默认为原名字")
    private String displayTitle;
    @Schema(description="气球颜色")
    private String color;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
