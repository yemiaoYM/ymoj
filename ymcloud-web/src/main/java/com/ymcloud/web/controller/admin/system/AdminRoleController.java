package com.ymcloud.web.controller.admin.system;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.RoleDTO;
import com.ymcloud.pojo.dto.admin.RoleAuthDTO;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.entity.Role;
import com.ymcloud.pojo.query.admin.AdminRolePageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理
 */
@Slf4j
@RestController
@RequestMapping("/admin/role")
public class AdminRoleController {

    @Autowired
    private AdminRoleService adminRoleService;

    /**
     * 分页查询角色列表
     * @param query 查询条件
     * @return 角色列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/list")
    public Result<PageVO<Role>> queryList(@Validated AdminRolePageQuery query) {
        return Result.ok(adminRoleService.queryList(query));
    }

    /**
     * 查询启用的角色选项
     * @return 角色选项
     */
    @PreAuthorize("@ss.hasAnyPermi('system:role:list', 'user:user:role')")
    @GetMapping("/options")
    public Result<List<Role>> listOptions() {
        return Result.ok(adminRoleService.listOptions());
    }

    /**
     * 根据ID查询角色
     * @param id 角色ID
     * @return 角色信息
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/{id}")
    public Result<Role> getRoleById(@PathVariable("id") Long id) {
        return Result.ok(adminRoleService.getRoleById(id));
    }

    /**
     * 新增角色
     * @param roleDTO 角色信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @PostMapping
    public Result<Void> addRole(@Validated @RequestBody RoleDTO roleDTO) {
        log.info("新增角色:{}", roleDTO);
        adminRoleService.addRole(roleDTO);
        return Result.ok();
    }

    /**
     * 修改角色
     * @param id 角色ID
     * @param roleDTO 角色信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateRole(@PathVariable("id") Long id, @Validated @RequestBody RoleDTO roleDTO) {
        log.info("修改角色:{}，数据:{}", id, roleDTO);
        adminRoleService.updateRole(id, roleDTO);
        return Result.ok();
    }

    /**
     * 删除角色
     * @param id 角色ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable("id") Long id) {
        log.info("删除角色:{}", id);
        adminRoleService.deleteRole(id);
        return Result.ok();
    }

    /**
     * 查询角色授权所需的菜单和操作权限
     * @return 权限选项
     */
    @PreAuthorize("@ss.hasPermi('system:role:auth')")
    @GetMapping("/auth-options")
    public Result<List<Auth>> listAuthOptions() {
        return Result.ok(adminRoleService.listAuthOptions());
    }

    /**
     * 查询角色已分配的权限ID
     * @param id 角色ID
     * @return 权限ID列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:auth')")
    @GetMapping("/{id}/auth")
    public Result<List<Long>> getRoleAuthIds(@PathVariable("id") Long id) {
        return Result.ok(adminRoleService.getRoleAuthIds(id));
    }

    /**
     * 分配角色权限并补全上级菜单
     * @param id 角色ID
     * @param dto 权限分配信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:role:auth')")
    @PutMapping("/{id}/auth")
    public Result<Void> updateRoleAuths(@PathVariable("id") Long id, @Validated @RequestBody RoleAuthDTO dto) {
        log.info("分配角色权限:{}，数据:{}", id, dto);
        adminRoleService.updateRoleAuths(id, dto.getAuthIds());
        return Result.ok();
    }
}
