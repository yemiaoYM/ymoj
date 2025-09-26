package com.ymcloud.web.controller.admin.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.ResetUserPasswordDTO;
import com.ymcloud.pojo.dto.admin.UpdateUserStatusDTO;
import com.ymcloud.pojo.dto.admin.UserDTO;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.query.admin.AdminUserPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.admin.user
 * @ClassName: AdminUserController
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  19:27
 * @Description: 管理用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    /**
     * 查询用户列表
     * @param pageQuery 查询条件
     * @return 用户列表
     */
    @PreAuthorize("hasAuthority('user:user:list')")
    @GetMapping("/list")
    public Result<PageVO<User>> queryList(@Validated AdminUserPageQuery pageQuery) {
        log.info("查询用户列表:{}",pageQuery);
        PageVO<User> page = adminUserService.queryList(pageQuery);
        return Result.ok(page);
    }

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户信息
     */
    @PreAuthorize("hasAuthority('user:user:query')")
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable("id") Long id) {
        log.info("根据ID查询用户:{}", id);
        User user = adminUserService.getUserById(id);
        return Result.ok(user);
    }

    /**
     * 新增用户
     * @param user 用户信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('user:user:add')")
    @PostMapping
    public Result<Void> addUser(@RequestBody @Validated UserDTO user) {
        log.info("新增用户:{}", user);
        adminUserService.addUser(user);
        return Result.ok();
    }

    /**
     * 修改用户
     * @param user 用户信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('user:user:edit')")
    @PutMapping
    public Result<Void> updateUser(@Validated @RequestBody UserDTO user) {
        log.info("修改用户:{}", user);
        adminUserService.updateUser(user);
        return Result.ok();
    }

    /**
     * 根据ID删除用户
     * @param ids 用户ID
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('user:user:remove')")
    @DeleteMapping("/{ids}")
    public Result<?> deleteUserById(@PathVariable("ids") List<Long> ids) {
        log.info("根据ID删除用户:{}", ids);
        adminUserService.deleteUserById(ids);
        return Result.ok().msg("删除成功");
    }

    /**
     * 修改用户状态
     * @param dto 请求体
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('user:user:edit')")
    @PutMapping("/status")
    public Result<Void> updateUserStatus(@Validated @RequestBody UpdateUserStatusDTO dto) {
        log.info("修改用户状态:{}", dto);
        adminUserService.updateUserStatus(dto);
        return Result.ok();
    }

    /**
     * 重置用户密码
     * @param dto 请求体
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('user:user:edit')")
    @PutMapping("/reset-password")
    public Result<Void> resetPassword(@Validated @RequestBody ResetUserPasswordDTO dto){
        log.info("重置用户密码:{}",dto);
        adminUserService.resetPassword(dto);
        return Result.ok();
    }

}
