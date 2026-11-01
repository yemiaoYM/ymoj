package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.AuthEntityService;
import com.ymcloud.core.web.dao.RoleAuthEntityService;
import com.ymcloud.pojo.dto.admin.AuthDTO;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.entity.RoleAuth;
import com.ymcloud.web.service.admin.AdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-10
 * @Description: 菜单权限定义，仅超级管理员可修改
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    @Autowired
    private AuthEntityService authEntityService;

    @Autowired
    private RoleAuthEntityService roleAuthEntityService;

    /**
     * 查询菜单权限列表
     * @return 菜单权限列表
     */
    @Override
    public List<Auth> queryList() {
        return authEntityService.lambdaQuery()
                .orderByAsc(Auth::getId)
                .list();
    }

    /**
     * 新增菜单权限
     * @param authDTO 菜单权限信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAuth(AuthDTO authDTO) {
        checkSuperAdmin();
        Auth auth = BeanUtil.copyProperties(authDTO, Auth.class);
        auth.setBuiltIn(false);
        normalizeAuth(auth);
        validateAuth(auth);
        if (!authEntityService.save(auth)) {
            throw new ServiceException("新增菜单权限失败，请稍后再试");
        }
    }

    /**
     * 修改菜单权限
     * @param id 权限ID
     * @param authDTO 菜单权限信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAuth(Long id, AuthDTO authDTO) {
        checkSuperAdmin();
        Auth existing = getAuth(id);
        Auth auth = BeanUtil.copyProperties(authDTO, Auth.class);
        auth.setId(id);
        normalizeAuth(auth);
        if (!Objects.equals(existing.getCode(), auth.getCode()) || !existing.getType().equals(auth.getType())) {
            throw new ServiceException("权限创建后不能修改类型或编码");
        }
        validateAuth(auth);
        if (!authEntityService.updateById(auth)) {
            throw new ServiceException("修改菜单权限失败，请稍后再试");
        }
    }

    /**
     * 删除菜单权限
     * @param id 权限ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAuth(Long id) {
        checkSuperAdmin();
        getAuth(id);
        boolean exists = authEntityService.lambdaQuery()
                .eq(Auth::getParentId, id)
                .exists();
        if (exists) {
            throw new ServiceException("存在下级权限，请先删除下级权限");
        }
        roleAuthEntityService.lambdaUpdate()
                .eq(RoleAuth::getAuthId, id)
                .remove();
        if (!authEntityService.removeById(id)) {
            throw new ServiceException("删除菜单权限失败，请稍后再试");
        }
    }

    /**
     * 根据ID查询并校验权限
     * @param id 权限ID
     * @return 权限信息
     */
    private Auth getAuth(Long id) {
        if (id == null || id <= 0) {
            throw new ServiceException("权限ID无效");
        }
        Auth auth = authEntityService.getById(id);
        if (auth == null) {
            throw new ServiceException("权限不存在");
        }
        return auth;
    }

    /**
     * 校验当前用户是否为超级管理员
     */
    private void checkSuperAdmin() {
        //权限定义影响所有角色，只允许超级管理员维护
        if (!SecurityUtils.isSuperAdmin(SecurityUtils.getUserId())) {
            throw new ServiceException("只有超级管理员可以维护菜单权限定义", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * 根据权限类型清理不适用的字段
     * @param auth 权限信息
     */
    private void normalizeAuth(Auth auth) {
        if (StrUtil.isBlank(auth.getCode())) {
            auth.setCode(null);
        }
        if ("M".equals(auth.getType())) {
            auth.setCode(null);
            auth.setComponent("Layout");
        } else if ("F".equals(auth.getType())) {
            auth.setPath(null);
            auth.setRouteName(null);
            auth.setComponent(null);
            auth.setIcon(null);
        }
    }

    /**
     * 校验权限编码、上级关系和路由配置
     * @param auth 权限信息
     */
    private void validateAuth(Auth auth) {
        if (!"M".equals(auth.getType()) && StrUtil.isBlank(auth.getCode())) {
            throw new ServiceException("页面和操作权限必须配置权限编码");
        }
        if (auth.getCode() != null) {
            boolean exists = authEntityService.lambdaQuery()
                    .eq(Auth::getCode, auth.getCode())
                    .ne(auth.getId() != null, Auth::getId, auth.getId())
                    .exists();
            if (exists) {
                throw new ServiceException("权限编码已存在");
            }
        }
        if (auth.getParentId() == null) {
            throw new ServiceException("请选择上级权限");
        }
        if (auth.getParentId() == 0L) {
            if (!"M".equals(auth.getType()) && !"admin:access".equals(auth.getCode())) {
                throw new ServiceException("页面或操作权限必须选择上级菜单");
            }
        } else {
            Auth parent = getAuth(auth.getParentId());
            if (("C".equals(auth.getType()) && !"M".equals(parent.getType()))
                    || ("F".equals(auth.getType()) && !"C".equals(parent.getType()))
                    || "M".equals(auth.getType())) {
                throw new ServiceException("目录位于顶层，页面位于目录下，操作权限位于页面下");
            }
            Set<Long> visited = new HashSet<>();
            if (auth.getId() != null) {
                visited.add(auth.getId());
            }
            Long currentId = auth.getParentId();
            while (currentId != 0L) {
                if (!visited.add(currentId)) {
                    throw new ServiceException("上级权限不能指向自己或自己的下级");
                }
                currentId = getAuth(currentId).getParentId();
            }
        }
        if (!"F".equals(auth.getType())) {
            if (StrUtil.isBlank(auth.getRouteName()) || !auth.getRouteName().matches("^[A-Za-z][A-Za-z0-9_]*$")) {
                throw new ServiceException("请输入有效的路由名称");
            }
            boolean routeNameExists = authEntityService.lambdaQuery()
                    .eq(Auth::getRouteName, auth.getRouteName())
                    .ne(auth.getId() != null, Auth::getId, auth.getId())
                    .exists();
            if (routeNameExists) {
                throw new ServiceException("路由名称已存在");
            }
            String pathPattern = "M".equals(auth.getType())
                    ? "^/admin/[A-Za-z0-9_-]+$" : "^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*$";
            if (StrUtil.isBlank(auth.getPath()) || !auth.getPath().matches(pathPattern)) {
                throw new ServiceException("目录路径需以/admin/开头，页面路径使用相对路径");
            }
            boolean pathExists = authEntityService.lambdaQuery()
                    .eq(Auth::getParentId, auth.getParentId())
                    .eq(Auth::getPath, auth.getPath())
                    .ne(auth.getId() != null, Auth::getId, auth.getId())
                    .exists();
            if (pathExists) {
                throw new ServiceException("同一上级下的路由路径已存在");
            }
            if ("C".equals(auth.getType()) && (StrUtil.isBlank(auth.getComponent())
                    || !auth.getComponent().matches("^admin/[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*$"))) {
                throw new ServiceException("页面组件必须使用admin目录下的组件路径");
            }
        }
    }
}
