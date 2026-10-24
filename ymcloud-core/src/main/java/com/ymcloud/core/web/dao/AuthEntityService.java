package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Auth;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: AuthEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  17:07
 * @Description: AuthEntityService
 */
public interface AuthEntityService extends IService<Auth> {

    /**
     * 查询全部可展示菜单，授权由业务层决定。
     * @return 菜单列表
     */
    List<Auth> selectMenuTreeAll();

    /**
     * 查询指定用户被授权的菜单。
     * @param userId 已认证用户编号
     * @return 菜单列表
     */
    List<Auth> selectMenuTreeByUserId(Long userId);
}
