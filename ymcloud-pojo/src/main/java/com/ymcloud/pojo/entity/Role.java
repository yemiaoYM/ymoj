package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色表。 */
@Data
public class Role {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码（如 ADMIN, USER） */
    private String code;

    /** 角色名称（中文名，如 管理员，普通用户） */
    private String name;

    /** 角色描述 */
    private String description;

    /** 角色状态：0=禁用，1=启用 */
    private Integer status;

    /** 是否为系统内置角色 */
    private Boolean builtIn;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
