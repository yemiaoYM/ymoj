package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.ResetUserPasswordDTO;
import com.ymcloud.pojo.dto.admin.UpdateUserStatusDTO;
import com.ymcloud.pojo.dto.admin.UserDTO;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.pojo.query.admin.AdminUserPageQuery;
import com.ymcloud.pojo.vo.PageVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: AdminUserService
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:52
 * @Description: AdminUserService
 */
public interface AdminUserService {

    /**
     * 查询用户列表
     * @param pageQuery 查询条件
     * @return 用户列表
     */
    PageVO<User> queryList(AdminUserPageQuery pageQuery);

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户
     */
    User getUserById(Long id);

    /**
     * 根据ID删除用户
     * @param ids 用户ID
     */
    void deleteUserById(List<Long> ids);

    /**
     * 修改用户状态
     * @param dto 请求体
     */
    void updateUserStatus(UpdateUserStatusDTO dto);

    /**
     * 重置用户密码
     * @param dto 请求体
     */
    void resetPassword(ResetUserPasswordDTO dto);

    /**
     * 新增用户
     * @param user 用户
     */
    void addUser(UserDTO user);

    /**
     * 修改用户
     * @param user 用户
     */
    void updateUser(UserDTO user);
}
