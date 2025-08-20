package com.ymcloud.pojo.entity;


import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "权限表")
public class Auth {
    @TableId(type = IdType.AUTO)
    @Schema(description="主键")
    private Long id;
    @Schema(description="权限编码（如 problem:add, user:delete）")
    private String code;
    @Schema(description="权限名称（中文名，如 新增题目，删除用户）")
    private String name;
    @Schema(description="父权限ID")
    private Long parentId;
    @Schema(description="路由路径")
    private String path;
    @Schema(description="路由名称")
    private String routeName;
    @Schema(description="前端组件路径（菜单权限时使用）")
    private String component;
    @Schema(description="前端菜单图标")
    private String icon;
    @Schema(description="权限状态：0=禁用，1=启用")
    private Integer status;
    @Schema(description="创建时间")
    private LocalDateTime createdTime;
    @Schema(description="更新时间")
    private LocalDateTime updatedTime;
}
