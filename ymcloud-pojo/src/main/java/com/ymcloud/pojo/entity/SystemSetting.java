package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "系统配置")
public class SystemSetting {
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;
    @Schema(description = "配置键")
    private String settingKey;
    @Schema(description = "配置值")
    private String settingValue;
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
