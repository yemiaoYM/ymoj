package com.ymcloud.web;

import com.ymcloud.common.constant.PermissionConstants;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.AuthorizationService;
import com.ymcloud.core.web.dao.RoleEntityService;
import com.ymcloud.core.web.dao.UserRoleEntityService;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.core.web.mapper.RoleMapper;
import com.ymcloud.core.web.mapper.UserMapper;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 实时授权测试，不读取真实用户或令牌
 */
@ExtendWith(MockitoExtension.class)
public class RbacAuthorizationTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private RoleEntityService roleEntityService;

    @Mock
    private UserRoleEntityService userRoleEntityService;

    @InjectMocks
    private AuthorizationService authorizationService;

    @Test
    void revocationUpdatesPreviouslyCachedAuthoritiesOnNextRequest() {
        User user = new User();
        user.setId(9L);
        user.setStatus(UserStatus.OK);
        LoginUser loginUser = new LoginUser(user, List.of("EDITOR"), List.of("admin:access", "system:setting:edit"));
        when(userMapper.selectById(9L)).thenReturn(user);
        when(roleMapper.selectRolesByUserId(9L)).thenReturn(List.of("EDITOR"));
        when(authMapper.selectPermsByUserId(9L)).thenReturn(List.of("admin:access"));
        assertTrue(authorizationService.refreshAuthorization(loginUser));
        assertFalse(loginUser.getAuthorities().stream().anyMatch(a -> "system:setting:edit".equals(a.getAuthority())));
        assertTrue(loginUser.getAuthorities().stream().anyMatch(a -> "admin:access".equals(a.getAuthority())));
    }

    @Test
    void disabledOrDeletedAccountCannotContinueUsingCachedAuthorization() {
        User user = new User();
        user.setId(9L);
        user.setStatus(UserStatus.DISABLE);
        LoginUser loginUser = new LoginUser(user);
        when(userMapper.selectById(9L)).thenReturn(user).thenReturn(null);
        assertFalse(authorizationService.refreshAuthorization(loginUser));
        assertFalse(authorizationService.refreshAuthorization(loginUser));
        verifyNoInteractions(roleMapper, authMapper);
    }

    @Test
    void superAdminDoesNotDependOnEditableRoleMappings() {
        User user = new User();
        user.setId(1L);
        LoginUser loginUser = new LoginUser(user);
        authorizationService.loadAuthorities(loginUser);
        assertEquals(List.of("ADMIN"), loginUser.getRoles());
        assertEquals(List.of(PermissionConstants.ALL_PERMISSION), loginUser.getPermissions());
        verifyNoInteractions(roleMapper, authMapper);
    }
}
