package com.ymcloud.pojo.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: MetaVo
 * @Author: Yemiao
 * @CreateTime: 2025-09-17  22:14
 * @Description: 路由显示信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaVo {

    /**
     * 设置该路由在侧边栏和面包屑中展示的名字
     */
    private String title;

    /**
     * 设置该路由的图标
     */
    private String icon;

    /**
     * 是否要求登录
     */
    private Boolean requireLogin = true;
}
