package com.ymcloud.core.security.service;

import com.ymcloud.common.constant.PermissionConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.web.dao.RoleEntityService;
import com.ymcloud.core.web.dao.UserRoleEntityService;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.core.web.mapper.RoleMapper;
import com.ymcloud.core.web.mapper.UserMapper;
import com.ymcloud.pojo.entity.Role;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.entity.UserRole;
import com.ymcloud.pojo.enums.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户实时授权服务
 */
@Service
public class AuthorizationService {

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleEntityService roleEntityService;

    @Autowired
    private UserRoleEntityService userRoleEntityService;

    /**
     * 刷新当前授权，避免缓存继续保留已经撤销的权限
     * @param loginUser 登录用户
     * @return 账户是否可用
     */
    public boolean refreshAuthorization(LoginUser loginUser) {
        User user = userMapper.selectById(loginUser.getUser().getId());
        if (user == null || user.getStatus() != UserStatus.OK) {
            return false;
        }
        loginUser.setUser(user);
        loadAuthorities(loginUser);
        return true;
    }

    /**
     * 加载角色和权限，超级管理员保留全部系统权限
     * @param loginUser 登录用户
     */
    public void loadAuthorities(LoginUser loginUser) {
        Long userId = loginUser.getUser().getId();
        List<String> roles = userId.equals(1L) ? List.of("ADMIN") : roleMapper.selectRolesByUserId(userId);
        loginUser.setRoles(roles.stream()
                .sorted()
                .toList());
        List<String> permissions = userId.equals(1L)
                ? List.of(PermissionConstants.ALL_PERMISSION) : authMapper.selectPermsByUserId(userId);
        loginUser.setPermissions(permissions.stream()
                .sorted()
                .toList());
    }

    /**
     * 为新用户分配普通用户角色
     * @param userId 用户ID
     */
    public void assignDefaultRole(Long userId) {
        Role role = roleEntityService.lambdaQuery()
                .eq(Role::getCode, "USER")
                .eq(Role::getStatus, 1)
                .one();
        if (role == null) {
            throw new ServiceException("默认用户角色未配置，请联系管理员");
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        if (!userRoleEntityService.save(userRole)) {
            throw new ServiceException("分配默认角色失败，请稍后再试");
        }
    }
}
