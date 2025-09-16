package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.vo.admin.Menu;
import com.ymcloud.web.service.admin.AdminMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: AdminMenuServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-15  22:38
 * @Description: AdminMenuServiceImpl
 */
@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    @Autowired
    private AuthMapper authMapper;

    /**
     * 获取菜单树
     * @return 菜单树
     */
    @Override
    public List<Menu> selectMenuTree() {
        Long userId = SecurityUtils.getUserId();
        List<Auth> auths = new ArrayList<>();
        //超级管理员获取所有菜单
        if(SecurityUtils.isSuperAdmin(userId)){
            auths = authMapper.selectMenuTreeAll();
        }else{
            //否则获取对应菜单
            auths = authMapper.selectMenuTreeByUserId(userId);
        }
        //构建菜单树
        return buildMenuTree(auths);
    }


    /**
     * 构建菜单树
     *
     * @param authList 权限列表
     * @return 菜单树
     */
    public static List<Menu> buildMenuTree(List<Auth> authList) {
        if (CollUtil.isEmpty(authList)){
            return List.of();
        }
        //先转成 Menu
        Map<Long, Menu> menuMap = new HashMap<>();
        for (Auth auth : authList) {
            Menu menu = BeanUtil.copyProperties(auth, Menu.class);
            menuMap.put(auth.getId(), menu);
        }
        //构建树
        List<Menu> rootMenus = new ArrayList<>();
        for (Auth auth : authList) {
            Menu menu = menuMap.get(auth.getId());
            if (auth.getParentId() == 0) {
                // 根菜单
                rootMenus.add(menu);
            } else {
                // 挂到父菜单
                Menu parent = menuMap.get(auth.getParentId());
                if (parent != null) {
                    parent.getChildren().add(menu);
                }
            }
        }

        return rootMenus;
    }

}
