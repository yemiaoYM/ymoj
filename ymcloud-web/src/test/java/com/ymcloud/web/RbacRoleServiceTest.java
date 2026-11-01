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
import com.ymcloud.core.web.mapper.RoleAuthMapper;
import com.ymcloud.core.web.mapper.RoleMapper;
import com.ymcloud.core.web.mapper.UserRoleMapper;
import com.ymcloud.pojo.dto.admin.RoleDTO;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.entity.Role;
import com.ymcloud.pojo.entity.RoleAuth;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.entity.UserRole;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.service.admin.impl.AdminRoleServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * @Author: Yemiao
 * @CreateTime: 2026-10-10
 * @Description: 验证只有超级管理员可以分配角色及调整授权
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
        user.setStatus(UserStatus.OK);
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

    /**
     * 角色和通配权限不能替代 ID 1 的授权身份，所有写入口均在访问数据库前拒绝
     * @param role 当前用户角色编码
     */
    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN", "PROBLEM_SETTER", "CONTEST_MANAGER", "SUPER_ADMIN"})
    void onlySuperAdminCanWriteRoleConfiguration(String role) {
        setLogin(9L, List.of(role), List.of("*:*:*"));
        assertThrows(ServiceException.class, () -> service.addRole(new RoleDTO()));
        assertThrows(ServiceException.class, () -> service.updateRole(4L, new RoleDTO()));
        assertThrows(ServiceException.class, () -> service.deleteRole(4L));
        assertThrows(ServiceException.class, () -> service.updateRoleAuths(4L, List.of()));
        assertThrows(ServiceException.class, () -> service.updateUserRoles(8L, List.of()));
        verifyNoInteractions(roleEntityService, authEntityService, roleAuthEntityService,
                userEntityService, userRoleEntityService);
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

    /**
     * 超管身份固定，不允许通过角色分配修改 ID 1
     */
    @Test
    void cannotAssignRolesToSuperAdmin() {
        setLogin(1L, List.of("USER"), List.of());
        when(userEntityService.getById(1L)).thenReturn(new User());
        assertThrows(ServiceException.class, () -> service.updateUserRoles(1L, List.of()));
        verifyNoInteractions(userRoleEntityService, roleEntityService);
    }

    /**
     * 超管可以分配普通管理员角色
     * 停用角色不能分配，且不会提前清除原有授权
     */
    @Test
    void superAdminCanAssignAdminButNotDisabledRoles() {
        setLogin(1L, List.of("USER"), List.of());
        when(userEntityService.getById(8L)).thenReturn(new User());
        Role selected = role(4L, "ADMIN");
        when(roleEntityService.listByIds(any())).thenReturn(List.of(selected));
        when(roleAuthMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(userRoleEntityService.saveBatch(any())).thenReturn(true);
        service.updateUserRoles(8L, List.of(4L));
        verify(userRoleEntityService).saveBatch(any());
        selected.setStatus(0);
        assertThrows(ServiceException.class, () -> service.updateUserRoles(8L, List.of(4L)));
        verify(userRoleMapper, times(1)).delete(any(Wrapper.class));
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
        setLogin(1L, List.of("ADMIN"), List.of());
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
        service.updateUserRoles(8L, List.of());
        verify(userRoleMapper).delete(any(Wrapper.class));
        verify(userRoleEntityService, never()).saveBatch(any());
    }
}
