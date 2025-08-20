package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "角色权限关联表")
public class RoleAuth {
    @TableId(type = IdType.AUTO)
    @Schema(description="主键")
    private Long id;
    @Schema(description="角色ID")
    private Long roleId;
    @Schema(description="权限ID")
    private Long authId;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
