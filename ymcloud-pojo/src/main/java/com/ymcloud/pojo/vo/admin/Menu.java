package com.ymcloud.pojo.vo.admin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: Menu
 * @Author: Yemiao
 * @CreateTime: 2025-09-15  22:32
 * @Description: 菜单
 */
@Data
public class Menu implements Serializable {

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
    /**
     * 子菜单
     */
    private List<Menu> children = new ArrayList<>();
}
