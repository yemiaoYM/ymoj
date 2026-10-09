package com.ymcloud.web;

import com.ymcloud.common.constant.PermissionConstants;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全权限匹配及超级管理员身份校验
 */
public class RbacPermissionServiceTest {

    private final PermissionService permissionService = new PermissionService();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private Authentication authentication(Long userId, UserStatus status, List<String> permissions) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        LoginUser loginUser = new LoginUser(user, List.of("ADMIN"), permissions);
        return new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
    }

    @Test
    void superAdminCanUsePermissionsNotYetDefinedInDatabase() {
        Authentication authentication = authentication(1L, UserStatus.OK, List.of(PermissionConstants.ALL_PERMISSION));
        assertTrue(permissionService.hasPermi(authentication, "future:feature:write"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertTrue(permissionService.hasAnyPermi("future:feature:read", "future:feature:write"));
    }

    @Test
    void ordinaryAdminRoleCannotUseSuperAdminWildcard() {
        Authentication authentication = authentication(9L, UserStatus.OK,
                List.of(PermissionConstants.ALL_PERMISSION, "system:auth:list"));
        assertTrue(permissionService.hasPermi(authentication, "system:auth:list"));
        assertFalse(permissionService.hasPermi(authentication, "system:auth:remove"));
        assertFalse(permissionService.hasPermi(authentication, "future:feature:write"));
    }

    @Test
    void disabledSuperAdminCannotUseWildcardOrExplicitPermission() {
        Authentication authentication = authentication(1L, UserStatus.DISABLE,
                List.of(PermissionConstants.ALL_PERMISSION, "admin:access"));
        assertFalse(permissionService.hasPermi(authentication, "admin:access"));
        assertFalse(permissionService.hasPermi(authentication, "future:feature:write"));
    }

    @Test
    void anonymousAndUnauthenticatedPrincipalsCannotUsePermissions() {
        assertFalse(permissionService.hasPermi(null, "admin:access"));
        Authentication authenticated = authentication(1L, UserStatus.OK, List.of(PermissionConstants.ALL_PERMISSION));
        Authentication unauthenticated = new UsernamePasswordAuthenticationToken(authenticated.getPrincipal(), null);
        assertFalse(permissionService.hasPermi(unauthenticated, "future:feature:write"));
        assertFalse(permissionService.hasPermi(authenticated, ""));
    }
}
