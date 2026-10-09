package com.ymcloud.web;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.web.dao.AuthEntityService;
import com.ymcloud.core.web.dao.RoleAuthEntityService;
import com.ymcloud.core.web.dao.RoleEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.core.web.dao.UserRoleEntityService;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.core.web.mapper.RoleMapper;
import com.ymcloud.core.web.mapper.RoleAuthMapper;
import com.ymcloud.core.web.mapper.UserRoleMapper;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.entity.Role;
import com.ymcloud.pojo.entity.RoleAuth;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.entity.UserRole;
import com.ymcloud.web.service.admin.impl.AdminRoleServiceImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 角色和用户分配的越权保护
 */
@ExtendWith(MockitoExtension.class)
public class RbacRoleServiceTest {

    @Mock
    private RoleEntityService roleEntityService;

    @Mock
    private AuthEntityService authEntityService;

    @Mock
    private RoleAuthEntityService roleAuthEntityService;

    @Mock
    private UserRoleEntityService userRoleEntityService;

    @Mock
    private UserEntityService userEntityService;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private RoleAuthMapper roleAuthMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @InjectMocks
    private AdminRoleServiceImpl service;

    @BeforeEach
    void login() {
        setLogin(9L, List.of("EDITOR"), List.of("system:role:auth", "admin:access"));
    }

    @BeforeEach
    void initQueryChains() {
        for (Class<?> entity : List.of(Role.class, Auth.class, RoleAuth.class, UserRole.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), entity);
        }
        //使用真实链式查询构建条件，仅在Mapper层模拟数据库返回
        lenient().when(roleEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(roleMapper, Role.class));
        lenient().when(authEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(authMapper, Auth.class));
        lenient().when(roleAuthEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(roleAuthMapper, RoleAuth.class));
        lenient().when(userRoleEntityService.lambdaQuery())
                .thenAnswer(invocation -> new LambdaQueryChainWrapper<>(userRoleMapper, UserRole.class));
        lenient().when(roleAuthEntityService.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(roleAuthMapper));
        lenient().when(userRoleEntityService.lambdaUpdate())
                .thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(userRoleMapper));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void setLogin(Long id, List<String> roles, List<String> permissions) {
        User user = new User();
        user.setId(id);
        LoginUser login = new LoginUser(user, roles, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, login.getAuthorities()));
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setStatus(1);
        role.setBuiltIn(false);
        return role;
    }

    private Auth auth(Long id, Long parent, String type, String code) {
        Auth auth = new Auth();
        auth.setId(id);
        auth.setParentId(parent);
        auth.setType(type);
        auth.setCode(code);
        return auth;
    }

    @Test
    void cannotGrantPermissionThatOperatorDoesNotHold() {
        when(roleEntityService.getById(4L)).thenReturn(role(4L, "VIEWER"));
        when(authMapper.selectList(any(Wrapper.class))).thenReturn(List.of(auth(10L, 0L, "F", "user:user:remove")));
        assertThrows(ServiceException.class, () -> service.updateRoleAuths(4L, List.of(10L)));
        verifyNoInteractions(roleAuthEntityService);
    }

    @Test
    void ownRoleAndAdminRoleCannotBeMutatedByDelegatedOperator() {
        when(roleEntityService.getById(4L)).thenReturn(role(4L, "EDITOR"), role(4L, "ADMIN"));
        assertThrows(ServiceException.class, () -> service.updateRoleAuths(4L, List.of()));
        assertThrows(ServiceException.class, () -> service.updateRoleAuths(4L, List.of()));
        verifyNoInteractions(roleAuthEntityService);
    }

    @Test
    void permissionGrantCompletesAncestorsAndEmptySelectionClearsGrant() {
        setLogin(1L, List.of("ADMIN"), List.of());
        when(roleEntityService.getById(4L)).thenReturn(role(4L, "VIEWER"));
        when(authMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(auth(10L, 0L, "M", null), auth(11L, 10L, "C", "example:list")));
        when(roleAuthEntityService.saveBatch(any())).thenReturn(true);
        service.updateRoleAuths(4L, List.of(11L, 11L));
        ArgumentCaptor<List<RoleAuth>> captured = ArgumentCaptor.forClass(List.class);
        verify(roleAuthEntityService).saveBatch(captured.capture());
        assertEquals(2, captured.getValue().size());
        assertTrue(captured.getValue().stream().anyMatch(a -> a.getAuthId().equals(10L)));
        service.updateRoleAuths(4L, List.of());
        verify(roleAuthMapper, times(2)).delete(any(Wrapper.class));
        verify(roleAuthEntityService, times(1)).saveBatch(any());
    }

    @Test
    void cannotAssignRolesToSuperAdminOrSelf() {
        when(userEntityService.getById(any(Long.class))).thenReturn(new User());
        assertThrows(ServiceException.class, () -> service.updateUserRoles(1L, List.of()));
        assertThrows(ServiceException.class, () -> service.updateUserRoles(9L, List.of()));
        verifyNoInteractions(userRoleEntityService, roleEntityService);
    }

    @Test
    void cannotAssignAdminOrDisabledRole() {
        when(userEntityService.getById(8L)).thenReturn(new User());
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        Role disabled = role(4L, "VIEWER");
        disabled.setStatus(0);
        when(roleEntityService.getById(4L)).thenReturn(role(4L, "ADMIN"), disabled);
        assertThrows(ServiceException.class, () -> service.updateUserRoles(8L, List.of(4L)));
        assertThrows(ServiceException.class, () -> service.updateUserRoles(8L, List.of(4L)));
        verify(userRoleMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void builtInAndAssignedRolesCannotBeDeleted() {
        setLogin(1L, List.of("ADMIN"), List.of());
        Role builtIn = role(4L, "USER");
        builtIn.setBuiltIn(true);
        when(roleEntityService.getById(4L)).thenReturn(builtIn, role(4L, "VIEWER"));
        assertThrows(ServiceException.class, () -> service.deleteRole(4L));
        when(userRoleMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.deleteRole(4L));
        verify(roleEntityService, never()).removeById(4L);
    }

    @Test
    void generalOptionsOnlyIncludeEnabledRoles() {
        when(roleMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<Role> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("status ="));
            assertTrue(query.getSqlSegment().contains("ORDER BY id ASC"));
            assertTrue(((LambdaQueryWrapper<Role>) query).getParamNameValuePairs().containsValue(1));
            return List.of(role(4L, "VIEWER"));
        });
        assertEquals(List.of(4L), service.listOptions().stream().map(Role::getId).toList());
    }

    @Test
    void assignedDisabledRoleCanBeDisplayedAndRemoved() {
        when(userEntityService.getById(8L)).thenReturn(new User());
        UserRole assigned = new UserRole();
        assigned.setUserId(8L);
        assigned.setRoleId(4L);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(assigned));
        Role disabled = role(4L, "VIEWER");
        disabled.setStatus(0);
        when(roleMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<Role> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("OR id IN"));
            return List.of(disabled);
        });
        assertEquals("VIEWER", service.listUserRoleOptions(8L).get(0).getCode());
        when(roleEntityService.getById(4L)).thenReturn(disabled);
        service.updateUserRoles(8L, List.of());
        verify(userRoleMapper).delete(any(Wrapper.class));
        verify(userRoleEntityService, never()).saveBatch(any());
    }
}
