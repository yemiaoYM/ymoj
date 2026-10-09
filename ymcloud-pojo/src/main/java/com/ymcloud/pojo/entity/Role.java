package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description="角色表")
public class Role {
    @TableId(type = IdType.AUTO)
    @Schema(description="主键")
    private Long id;
    @Schema(description="角色编码（如 ADMIN, USER）")
    private String code;
    @Schema(description="角色名称（中文名，如 管理员，普通用户）")
    private String name;
    @Schema(description="角色描述")
    private String description;
    @Schema(description="角色状态：0=禁用，1=启用")
    private Integer status;
    @Schema(description="是否为系统内置角色")
    private Boolean builtIn;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
