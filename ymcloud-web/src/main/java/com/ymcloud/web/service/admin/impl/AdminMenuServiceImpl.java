package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.collection.CollUtil;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.AuthEntityService;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.vo.admin.MetaVo;
import com.ymcloud.pojo.vo.admin.RouterVo;
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
    private AuthEntityService authEntityService;

    /**
     * 获取菜单树
     * @return 菜单树
     */
    @Override
    public List<RouterVo> selectMenuTree() {
        Long userId = SecurityUtils.getUserId();
        List<Auth> auths = new ArrayList<>();
        //超级管理员获取所有菜单
        if(SecurityUtils.isSuperAdmin(userId)){
            auths = authEntityService.selectMenuTreeAll();
        }else{
            //否则获取对应菜单
            auths = authEntityService.selectMenuTreeByUserId(userId);
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
    public static List<RouterVo> buildMenuTree(List<Auth> authList) {
        if (CollUtil.isEmpty(authList)){
            return List.of();
        }
        //先转成 RouterVo
        Map<Long, RouterVo> routerVoMap = new HashMap<>();
        for (Auth auth : authList) {
            RouterVo router = new RouterVo();
            router.setName(auth.getRouteName());
            router.setPath(auth.getPath());
            router.setComponent(auth.getComponent());
            MetaVo meta = new MetaVo(auth.getName(), auth.getIcon(), true);
            router.setMeta(meta);
            routerVoMap.put(auth.getId(), router);
        }
        //构建树
        List<RouterVo> rootMenus = new ArrayList<>();
        for (Auth auth : authList) {
            RouterVo router = routerVoMap.get(auth.getId());
            if (auth.getParentId() == 0) {
                //根菜单
                rootMenus.add(router);
            } else {
                //挂到父菜单
                RouterVo parent = routerVoMap.get(auth.getParentId());
                if (parent != null) {
                    parent.getChildren().add(router);
                }
            }
        }
        return rootMenus;
    }

}
