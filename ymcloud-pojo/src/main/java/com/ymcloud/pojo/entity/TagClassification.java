package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签分类表")
public class TagClassification {

    @TableId(type = IdType.AUTO)
    @Schema(description="主键")
    private Long id;
    @Schema(description="标签分类名称")
    private String name;
    @Schema(description="分类优先级（数值越小优先级越高）")
    private Integer priority;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
