package com.ymcloud.web.controller.admin.system;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.AuthDTO;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.web.service.admin.AdminAuthService;
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
 * 菜单权限管理
 */
@Slf4j
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    @Autowired
    private AdminAuthService adminAuthService;

    /**
     * 查询菜单权限列表
     * @return 菜单权限列表
     */
    @PreAuthorize("@ss.hasPermi('system:auth:list')")
    @GetMapping("/list")
    public Result<List<Auth>> queryList() {
        return Result.ok(adminAuthService.queryList());
    }

    /**
     * 新增菜单权限
     * @param authDTO 菜单权限信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:auth:add')")
    @PostMapping
    public Result<Void> addAuth(@Validated @RequestBody AuthDTO authDTO) {
        log.info("新增菜单权限:{}", authDTO);
        adminAuthService.addAuth(authDTO);
        return Result.ok();
    }

    /**
     * 修改菜单权限
     * @param id 权限ID
     * @param authDTO 菜单权限信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:auth:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateAuth(@PathVariable("id") Long id, @Validated @RequestBody AuthDTO authDTO) {
        log.info("修改菜单权限:{}，数据:{}", id, authDTO);
        adminAuthService.updateAuth(id, authDTO);
        return Result.ok();
    }

    /**
     * 删除菜单权限
     * @param id 权限ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:auth:remove')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAuth(@PathVariable("id") Long id) {
        log.info("删除菜单权限:{}", id);
        adminAuthService.deleteAuth(id);
        return Result.ok();
    }
}
