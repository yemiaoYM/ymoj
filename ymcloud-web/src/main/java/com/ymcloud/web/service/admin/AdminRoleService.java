package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.RoleDTO;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.entity.Role;
import com.ymcloud.pojo.query.admin.AdminRolePageQuery;
import com.ymcloud.pojo.vo.common.PageVO;

import java.util.List;

/**
 * 角色管理及用户授权服务
 */
public interface AdminRoleService {

    /**
     * 分页查询角色列表
     * @param query 查询条件
     * @return 角色列表
     */
    PageVO<Role> queryList(AdminRolePageQuery query);

    /**
     * 查询启用的角色选项
     * @return 角色选项
     */
    List<Role> listOptions();

    /**
     * 根据ID查询角色
     * @param id 角色ID
     * @return 角色信息
     */
    Role getRoleById(Long id);

    /**
     * 新增角色
     * @param roleDTO 角色信息
     */
    void addRole(RoleDTO roleDTO);

    /**
     * 修改角色
     * @param id 角色ID
     * @param roleDTO 角色信息
     */
    void updateRole(Long id, RoleDTO roleDTO);

    /**
     * 删除角色
     * @param id 角色ID
     */
    void deleteRole(Long id);

    /**
     * 查询角色授权所需的菜单和操作权限
     * @return 权限选项
     */
    List<Auth> listAuthOptions();

    /**
     * 查询角色已分配的权限ID
     * @param id 角色ID
     * @return 权限ID列表
     */
    List<Long> getRoleAuthIds(Long id);

    /**
     * 分配角色权限并补全上级菜单
     * @param id 角色ID
     * @param authIds 权限ID列表
     */
    void updateRoleAuths(Long id, List<Long> authIds);

    /**
     * 查询用户已分配的角色ID
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> getUserRoleIds(Long userId);

    /**
     * 查询用户角色选项，包含已分配的停用角色
     * @param userId 用户ID
     * @return 角色选项
     */
    List<Role> listUserRoleOptions(Long userId);

    /**
     * 分配用户角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    void updateUserRoles(Long userId, List<Long> roleIds);
}
