package com.ymcloud.pojo.entity;


import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签表")
public class Tag implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description="标签ID")
    private Long id;
    @Schema(description="标签名称")
    private String name;
    @Schema(description="父标签ID")
    private Long parentId;
    @Schema(description="标签颜色（如 #ff6600）")
    private String color;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
