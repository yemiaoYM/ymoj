package com.ymcloud.core.security.service;

import cn.hutool.core.util.StrUtil;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.utils.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 统一权限匹配服务，超级管理员无需逐项分配权限
 */
@Service("ss")
public class PermissionService {

    /**
     * 判断当前用户是否拥有指定权限
     * @param permission 权限编码
     * @return 是否拥有权限
     */
    public boolean hasPermi(String permission) {
        return hasPermi(SecurityUtils.getAuthentication(), permission);
    }

    /**
     * 判断认证用户是否拥有指定权限
     * @param authentication 认证信息
     * @param permission 权限编码
     * @return 是否拥有权限
     */
    public boolean hasPermi(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated() || StrUtil.isBlank(permission)) {
            return false;
        }
        if (authentication.getPrincipal() instanceof LoginUser loginUser
                && (loginUser.getUser() == null || !loginUser.isEnabled())) {
            return false;
        }
        //全权限只属于ID为1且账户可用的超级管理员，普通角色不能通过通配符提权
        if (SecurityUtils.isSuperAdmin(authentication)) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    /**
     * 判断当前用户是否拥有任意一项权限
     * @param permissions 权限编码列表
     * @return 是否拥有权限
     */
    public boolean hasAnyPermi(String... permissions) {
        return permissions != null && Arrays.stream(permissions)
                .anyMatch(this::hasPermi);
    }
}
