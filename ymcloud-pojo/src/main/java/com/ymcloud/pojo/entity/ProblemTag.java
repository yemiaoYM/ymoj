package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "题目标签连接表")
public class ProblemTag {
    @Schema(description="题目ID")
    private Long problemId;
    @Schema(description="标签ID")
    private Long tagId;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
}
