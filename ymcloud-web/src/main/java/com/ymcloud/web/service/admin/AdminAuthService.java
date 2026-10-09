package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.AuthDTO;
import com.ymcloud.pojo.entity.Auth;

import java.util.List;

/**
 * 菜单权限管理服务
 */
public interface AdminAuthService {

    /**
     * 查询菜单权限列表
     * @return 菜单权限列表
     */
    List<Auth> queryList();

    /**
     * 新增菜单权限
     * @param authDTO 菜单权限信息
     */
    void addAuth(AuthDTO authDTO);

    /**
     * 修改菜单权限
     * @param id 权限ID
     * @param authDTO 菜单权限信息
     */
    void updateAuth(Long id, AuthDTO authDTO);

    /**
     * 删除菜单权限
     * @param id 权限ID
     */
    void deleteAuth(Long id);
}
