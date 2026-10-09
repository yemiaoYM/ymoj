package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.AuthEntityService;
import com.ymcloud.core.web.dao.RoleAuthEntityService;
import com.ymcloud.core.web.dao.RoleEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.core.web.dao.UserRoleEntityService;
import com.ymcloud.pojo.dto.admin.RoleDTO;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.entity.Role;
import com.ymcloud.pojo.entity.RoleAuth;
import com.ymcloud.pojo.entity.UserRole;
import com.ymcloud.pojo.query.admin.AdminRolePageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理及用户授权服务实现类
 */
@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    @Autowired
    private RoleEntityService roleEntityService;

    @Autowired
    private AuthEntityService authEntityService;

    @Autowired
    private RoleAuthEntityService roleAuthEntityService;

    @Autowired
    private UserRoleEntityService userRoleEntityService;

    @Autowired
    private UserEntityService userEntityService;

    /**
     * 分页查询角色列表
     * @param query 查询条件
     * @return 角色列表
     */
    @Override
    public PageVO<Role> queryList(AdminRolePageQuery query) {
        Page<Role> page = roleEntityService.lambdaQuery()
                .like(StrUtil.isNotBlank(query.getName()), Role::getName, query.getName())
                .like(StrUtil.isNotBlank(query.getCode()), Role::getCode, query.getCode())
                .eq(query.getStatus() != null, Role::getStatus, query.getStatus())
                .orderByAsc(Role::getId)
                .page(Page.of(query.getPageNo(), query.getPageSize()));
        return PageVO.of(page, Role.class);
    }

    /**
     * 查询启用的角色选项
     * @return 角色选项
     */
    @Override
    public List<Role> listOptions() {
        return roleEntityService.lambdaQuery()
                .eq(Role::getStatus, 1)
                .orderByAsc(Role::getId)
                .list();
    }

    /**
     * 根据ID查询角色
     * @param id 角色ID
     * @return 角色信息
     */
    @Override
    public Role getRoleById(Long id) {
        if (id == null || id <= 0) {
            throw new ServiceException("角色ID无效");
        }
        Role role = roleEntityService.getById(id);
        if (role == null) {
            throw new ServiceException("角色不存在");
        }
        return role;
    }

    /**
     * 新增角色
     * @param roleDTO 角色信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRole(RoleDTO roleDTO) {
        boolean exists = roleEntityService.lambdaQuery()
                .eq(Role::getCode, roleDTO.getCode())
                .exists();
        if (exists) {
            throw new ServiceException("角色编码已存在");
        }
        Role role = BeanUtil.copyProperties(roleDTO, Role.class);
        role.setBuiltIn(false);
        if (!roleEntityService.save(role)) {
            throw new ServiceException("新增角色失败，请稍后再试");
        }
    }

    /**
     * 修改角色
     * @param id 角色ID
     * @param roleDTO 角色信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleDTO roleDTO) {
        Role existing = getRoleById(id);
        checkManageRole(existing);
        if (!existing.getCode().equals(roleDTO.getCode())) {
            throw new ServiceException("角色创建后不能修改编码");
        }
        if (Boolean.TRUE.equals(existing.getBuiltIn()) && !Objects.equals(roleDTO.getStatus(), 1)) {
            throw new ServiceException("不能停用系统内置角色");
        }
        Role role = BeanUtil.copyProperties(roleDTO, Role.class);
        role.setId(id);
        if (!roleEntityService.updateById(role)) {
            throw new ServiceException("修改角色失败，请稍后再试");
        }
    }

    /**
     * 删除角色
     * @param id 角色ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = getRoleById(id);
        checkManageRole(role);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new ServiceException("不能删除系统内置角色");
        }
        boolean exists = userRoleEntityService.lambdaQuery()
                .eq(UserRole::getRoleId, id)
                .exists();
        if (exists) {
            throw new ServiceException("角色已分配给用户，请先解除用户关联");
        }
        roleAuthEntityService.lambdaUpdate()
                .eq(RoleAuth::getRoleId, id)
                .remove();
        if (!roleEntityService.removeById(id)) {
            throw new ServiceException("删除角色失败，请稍后再试");
        }
    }

    /**
     * 查询角色授权所需的菜单和操作权限
     * @return 权限选项
     */
    @Override
    public List<Auth> listAuthOptions() {
        return authEntityService.lambdaQuery()
                .orderByAsc(Auth::getId)
                .list();
    }

    /**
     * 查询角色已分配的权限ID
     * @param id 角色ID
     * @return 权限ID列表
     */
    @Override
    public List<Long> getRoleAuthIds(Long id) {
        getRoleById(id);
        return roleAuthEntityService.lambdaQuery()
                .eq(RoleAuth::getRoleId, id)
                .list()
                .stream()
                .map(RoleAuth::getAuthId)
                .toList();
    }

    /**
     * 分配角色权限并补全上级菜单
     * @param id 角色ID
     * @param authIds 权限ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleAuths(Long id, List<Long> authIds) {
        checkManageRole(getRoleById(id));
        Set<Long> selected = validateIds(authIds);
        Map<Long, Auth> authMap = listAuthOptions()
                .stream()
                .collect(Collectors.toMap(Auth::getId, auth -> auth));
        //自动补全上级目录，保证勾选页面后可以在菜单中访问
        for (Long authId : new ArrayList<>(selected)) {
            Set<Long> visited = new HashSet<>();
            Long currentId = authId;
            while (currentId != null && currentId != 0L) {
                if (!visited.add(currentId) || !authMap.containsKey(currentId)) {
                    throw new ServiceException("权限或上级菜单不存在，请刷新后重试");
                }
                Auth auth = authMap.get(currentId);
                checkGrantAuth(auth);
                selected.add(currentId);
                currentId = auth.getParentId();
            }
        }
        roleAuthEntityService.lambdaUpdate()
                .eq(RoleAuth::getRoleId, id)
                .remove();
        List<RoleAuth> mappings = selected.stream()
                .map(authId -> {
                    RoleAuth mapping = new RoleAuth();
                    mapping.setRoleId(id);
                    mapping.setAuthId(authId);
                    return mapping;
                })
                .toList();
        if (!mappings.isEmpty() && !roleAuthEntityService.saveBatch(mappings)) {
            throw new ServiceException("分配角色权限失败，请稍后再试");
        }
    }

    /**
     * 查询用户已分配的角色ID
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Override
    public List<Long> getUserRoleIds(Long userId) {
        checkUserExists(userId);
        return userRoleEntityService.lambdaQuery()
                .eq(UserRole::getUserId, userId)
                .list()
                .stream()
                .map(UserRole::getRoleId)
                .toList();
    }

    /**
     * 查询用户角色选项，包含已分配的停用角色
     * @param userId 用户ID
     * @return 角色选项
     */
    @Override
    public List<Role> listUserRoleOptions(Long userId) {
        List<Long> assignedRoleIds = getUserRoleIds(userId);
        //保留已分配的停用角色，便于显示名称及解除关联
        return roleEntityService.lambdaQuery()
                .and(query -> query.eq(Role::getStatus, 1)
                        .or(!assignedRoleIds.isEmpty())
                        .in(!assignedRoleIds.isEmpty(), Role::getId, assignedRoleIds))
                .orderByAsc(Role::getId)
                .list();
    }

    /**
     * 分配用户角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        checkUserExists(userId);
        if (userId.equals(1L) || userId.equals(SecurityUtils.getUserId())) {
            throw new ServiceException("不能修改超级管理员或当前登录用户的角色", HttpStatus.FORBIDDEN);
        }
        Set<Long> selected = validateIds(roleIds);
        //同时校验被移除的角色，避免绕过管理员角色保护
        Set<Long> changed = new HashSet<>(selected);
        changed.addAll(getUserRoleIds(userId));
        for (Long roleId : changed) {
            Role role = getRoleById(roleId);
            if (!SecurityUtils.isSuperAdmin(SecurityUtils.getUserId()) && "ADMIN".equals(role.getCode())) {
                throw new ServiceException("只有超级管理员可以分配或解除管理员角色", HttpStatus.FORBIDDEN);
            }
            if (selected.contains(roleId)) {
                if (!Objects.equals(role.getStatus(), 1)) {
                    throw new ServiceException("不能分配已停用的角色");
                }
                for (Long authId : getRoleAuthIds(roleId)) {
                    Auth auth = authEntityService.getById(authId);
                    if (auth == null) {
                        throw new ServiceException("角色包含无效权限，请先检查角色配置");
                    }
                    checkGrantAuth(auth);
                }
            }
        }
        userRoleEntityService.lambdaUpdate()
                .eq(UserRole::getUserId, userId)
                .remove();
        List<UserRole> mappings = selected.stream()
                .map(roleId -> {
                    UserRole mapping = new UserRole();
                    mapping.setUserId(userId);
                    mapping.setRoleId(roleId);
                    return mapping;
                })
                .toList();
        if (!mappings.isEmpty() && !userRoleEntityService.saveBatch(mappings)) {
            throw new ServiceException("分配用户角色失败，请稍后再试");
        }
    }

    /**
     * 校验用户是否存在
     * @param userId 用户ID
     */
    private void checkUserExists(Long userId) {
        if (userId == null || userId <= 0 || userEntityService.getById(userId) == null) {
            throw new ServiceException("用户不存在");
        }
    }

    /**
     * 校验授权ID并去重
     * @param ids 授权ID列表
     * @return 去重后的ID集合
     */
    private Set<Long> validateIds(List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ServiceException("授权列表包含无效ID");
        }
        return new LinkedHashSet<>(ids);
    }

    /**
     * 校验当前用户是否可以维护角色
     * @param role 角色信息
     */
    private void checkManageRole(Role role) {
        if (!SecurityUtils.isSuperAdmin(SecurityUtils.getUserId())) {
            if ("ADMIN".equals(role.getCode()) || SecurityUtils.getLoginUser().getRoles().contains(role.getCode())) {
                throw new ServiceException("不能修改管理员角色或当前用户持有的角色", HttpStatus.FORBIDDEN);
            }
        }
    }

    /**
     * 校验当前用户是否可以授予权限
     * @param auth 权限信息
     */
    private void checkGrantAuth(Auth auth) {
        if (!SecurityUtils.isSuperAdmin(SecurityUtils.getUserId()) && !"M".equals(auth.getType())
                && !SecurityUtils.getLoginUser().getPermissions().contains(auth.getCode())) {
            throw new ServiceException("不能分配当前用户未持有的权限", HttpStatus.FORBIDDEN);
        }
    }
}
