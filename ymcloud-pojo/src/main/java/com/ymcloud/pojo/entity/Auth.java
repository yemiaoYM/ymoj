package com.ymcloud.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 权限表。 */
@Data
public class Auth {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码（如 problem:add, user:delete） */
    private String code;

    /** 权限名称（中文名，如 新增题目，删除用户） */
    private String name;

    /** 父权限ID */
    private Long parentId;

    /** 路由路径 */
    private String path;

    /** 路由名称 */
    private String routeName;

    /** 前端组件路径（菜单权限时使用） */
    private String component;

    /** 前端菜单图标 */
    private String icon;

    /** 权限类型：M=目录，C=页面，F=操作 */
    private String type;

    /** 是否为系统内置权限 */
    private Boolean builtIn;

    /** 权限状态：0=禁用，1=启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
