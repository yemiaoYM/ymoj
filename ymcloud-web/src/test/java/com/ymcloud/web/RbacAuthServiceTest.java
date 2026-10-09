package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.web.dao.AuthEntityService;
import com.ymcloud.core.web.dao.RoleAuthEntityService;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.core.web.mapper.RoleAuthMapper;
import com.ymcloud.pojo.dto.admin.AuthDTO;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.entity.RoleAuth;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.web.service.admin.impl.AdminAuthServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 超级管理员菜单维护和普通用户越权保护
 */
@ExtendWith(MockitoExtension.class)
public class RbacAuthServiceTest {

    @Mock
    private AuthEntityService authEntityService;

    @Mock
    private RoleAuthEntityService roleAuthEntityService;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private RoleAuthMapper roleAuthMapper;

    @InjectMocks
    private AdminAuthServiceImpl service;

    @BeforeEach
    void initQueryChains() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), Auth.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RoleAuth.class);
        lenient().when(authEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(authMapper, Auth.class));
        lenient().when(roleAuthEntityService.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(roleAuthMapper));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void login(Long id) {
        User user = new User();
        user.setId(id);
        LoginUser login = new LoginUser(user, List.of("ADMIN"), List.of("system:auth:edit"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, login.getAuthorities()));
    }

    private Auth protectedAuth() {
        Auth auth = new Auth();
        auth.setId(6L);
        auth.setName("角色管理");
        auth.setType("C");
        auth.setCode("system:role:list");
        auth.setBuiltIn(true);
        auth.setStatus(1);
        auth.setParentId(515L);
        auth.setPath("role");
        auth.setRouteName("AdminRoleList");
        auth.setComponent("admin/common/RoleList");
        return auth;
    }

    private AuthDTO dto(Auth auth) {
        AuthDTO dto = new AuthDTO();
        dto.setType(auth.getType());
        dto.setName(auth.getName());
        dto.setCode(auth.getCode());
        dto.setStatus(auth.getStatus());
        dto.setParentId(auth.getParentId());
        dto.setPath(auth.getPath());
        dto.setRouteName(auth.getRouteName());
        dto.setComponent(auth.getComponent());
        return dto;
    }

    @Test
    void delegatedAdminCannotChangeGlobalPermissionDefinitions() {
        login(9L);
        assertThrows(ServiceException.class, () -> service.addAuth(new AuthDTO()));
        assertThrows(ServiceException.class, () -> service.updateAuth(6L, new AuthDTO()));
        assertThrows(ServiceException.class, () -> service.deleteAuth(6L));
        verifyNoInteractions(authEntityService, roleAuthEntityService);
    }

    @Test
    void superAdminCanEditBuiltInMenuRoutesAndDisableIt() {
        login(1L);
        Auth existing = protectedAuth();
        when(authEntityService.getById(6L)).thenReturn(existing);
        Auth parent = new Auth();
        parent.setId(515L);
        parent.setType("M");
        parent.setParentId(0L);
        when(authEntityService.getById(515L)).thenReturn(parent);
        when(authEntityService.updateById(any(Auth.class))).thenReturn(true);
        AuthDTO dto = dto(existing);
        dto.setStatus(0);
        dto.setPath("roles");
        dto.setRouteName("AdminRoles");
        dto.setComponent("admin/user/UserList");
        assertDoesNotThrow(() -> service.updateAuth(6L, dto));
        ArgumentCaptor<Auth> captured = ArgumentCaptor.forClass(Auth.class);
        verify(authEntityService).updateById(captured.capture());
        assertEquals(0, captured.getValue().getStatus());
        assertEquals("roles", captured.getValue().getPath());
        assertEquals("admin/user/UserList", captured.getValue().getComponent());
        verifyNoInteractions(roleAuthEntityService);
    }

    @Test
    void permissionCodeCannotBeChangedAccidentally() {
        login(1L);
        Auth existing = protectedAuth();
        when(authEntityService.getById(6L)).thenReturn(existing);
        AuthDTO changedCode = dto(existing);
        changedCode.setCode("another:permission");
        assertThrows(ServiceException.class, () -> service.updateAuth(6L, changedCode));
        verify(authEntityService, never()).updateById(any());
    }

    @Test
    void superAdminCanDeleteBuiltInLeafAndRemoveRoleGrants() {
        login(1L);
        when(authEntityService.getById(6L)).thenReturn(protectedAuth());
        when(authEntityService.removeById(6L)).thenReturn(true);
        assertDoesNotThrow(() -> service.deleteAuth(6L));
        verify(roleAuthMapper).delete(any(Wrapper.class));
        verify(authEntityService).removeById(6L);
    }

    @Test
    void parentMenuCannotBeDeletedWhileItHasChildren() {
        login(1L);
        when(authEntityService.getById(6L)).thenReturn(protectedAuth());
        when(authMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.deleteAuth(6L));
        verify(authEntityService, never()).removeById(any(Long.class));
        verifyNoInteractions(roleAuthEntityService);
    }
}
