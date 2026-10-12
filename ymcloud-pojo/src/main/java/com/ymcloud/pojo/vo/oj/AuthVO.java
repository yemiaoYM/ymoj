package com.ymcloud.pojo.vo.oj;

import lombok.Data;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: AuthVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  21:16
 * @Description: 权限VO
 */
@Data
public class AuthVO {

    /**
     * 权限编码（如 problem:add, user:delete）
     */
    private String code;
    /**
     * 权限名称（中文名，如 新增题目，删除用户）
     */
    private String name;
    /**
     * 路由路径
     */
    private String path;
    /**
     * 路由名称
     */
    private String routeName;
    /**
     * 前端组件路径（菜单权限时使用）
     */
    private String component;
    /**
     * 前端菜单图标
     */
    private String icon;
}
