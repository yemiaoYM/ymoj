package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.user.UserException;
import com.ymcloud.common.exception.user.UserNotExistsException;
import com.ymcloud.core.security.service.AuthorizationService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.core.web.dao.UserRoleEntityService;
import com.ymcloud.pojo.dto.admin.ResetUserPasswordDTO;
import com.ymcloud.pojo.dto.admin.UpdateUserStatusDTO;
import com.ymcloud.pojo.dto.admin.UserDTO;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.entity.UserRole;
import com.ymcloud.pojo.query.admin.AdminUserPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: AdminUserServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:53
 * @Description: AdminUserServiceImpl
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    private UserEntityService userEntityService;

    @Autowired
    private UserRoleEntityService userRoleEntityService;

    @Autowired
    private AuthorizationService authorizationService;

    /**
     * 查询用户列表
     * @param pageQuery 查询条件
     * @return 用户列表
     */
    @Override
    public PageVO<User> queryList(AdminUserPageQuery pageQuery) {
        //创建分页实体
        Page<User> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        //条件分页查询用户列表
        Page<User> record = userEntityService.lambdaQuery()
                .like(StrUtil.isNotBlank(pageQuery.getUsername()), User::getUsername, pageQuery.getUsername())
                .eq(StrUtil.isNotBlank(pageQuery.getEmail()), User::getEmail, pageQuery.getEmail())
                .eq(pageQuery.getStatus() != null, User::getStatus, pageQuery.getStatus())
                .page(page);

        return PageVO.of(record, user -> {
            user.setPassword(null);
            return user;
        });
    }

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户
     */
    @Override
    public User getUserById(Long id) {
        User user = userEntityService.getById(id);
        if(user == null){
            throw new UserNotExistsException();
        }
        user.setPassword(null);
        return user;
    }

    /**
     * 根据ID删除用户
     * @param ids 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserById(List<Long> ids) {
        if(CollUtil.isEmpty(ids)){
            throw new UserException("用户id不能为空");
        }
        if(ids.contains(1L)){
            throw new UserException("对不起，不能删除超级管理员", HttpStatus.FORBIDDEN);
        }
        Long userId = SecurityUtils.getUserId();
        if(ids.contains(userId)){
            throw new UserException("不能删除当前登录用户");
        }
        int size = userEntityService.getBaseMapper().deleteByIds(ids);
        if(size != ids.size()){
            throw new UserException("删除用户失败，请稍后再试");
        }
        userRoleEntityService.lambdaUpdate()
                .in(UserRole::getUserId, ids)
                .remove();
    }

    /**
     * 修改用户状态
     * @param dto 请求体
     */
    @Override
    public void updateUserStatus(UpdateUserStatusDTO dto) {
        Long id = dto.getId();
        if(id == 1){
            throw new UserException("对不起，您不能修改超级管理员的状态！", HttpStatus.FORBIDDEN);
        }
        Long userId = SecurityUtils.getUserId();
        if(userId.equals(id)){
            throw new UserException("不能修改当前登录用户的状态");
        }
        boolean ok = userEntityService.lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getStatus, dto.getStatus())
                .update();
        if(!ok){
            throw new UserException("更新用户状态失败，请稍后再试");
        }
    }

    /**
     * 重置用户密码
     * @param dto 请求体
     */
    @Override
    public void resetPassword(ResetUserPasswordDTO dto) {
        Long id = dto.getId();
        if(id == 1){
            throw new UserException("对不起，您不能修改超级管理员的状态！", HttpStatus.FORBIDDEN);
        }
        Long userId = SecurityUtils.getUserId();
        if(userId.equals(id)){
            throw new UserException("不能修改当前登录用户的状态");
        }
        User user = userEntityService.getById(id);
        if (user == null){
            throw new UserNotExistsException();
        }
        if (SecurityUtils.matchesPassword(dto.getPassword(),user.getPassword())) {
            throw new UserException("与当前密码一致");
        }
        String encodePassword= SecurityUtils.encryptPassword(dto.getPassword());
        user.setPassword(encodePassword);
        boolean ok = userEntityService.updateById(user);
        if(!ok){
            throw new UserException("重置用户密码失败，请稍后再试");
        }
    }

    /**
     * 新增用户
     * @param user 用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUser(UserDTO user) {
        if(StrUtil.isBlank(user.getPassword())){
            throw new UserException("密码不能为空",HttpStatus.BAD_REQUEST);
        }
        //加密密码
        String encodePassword = SecurityUtils.encryptPassword(user.getPassword());
        user.setPassword(encodePassword);
        User newUser = BeanUtil.copyProperties(user, User.class);
        //检查用户名
        if (userEntityService.lambdaQuery().eq(User::getUsername, user.getUsername().trim()).exists()) {
            throw new UserException("用户名已存在");
        }
        //检查邮箱
        if (userEntityService.lambdaQuery().eq(User::getEmail, user.getEmail().trim()).exists()) {
            throw new UserException("此邮箱已被其他用户注册");
        }
        boolean ok = userEntityService.save(newUser);
        if (!ok) {
            throw new UserException("新增用户失败，请稍后再试");
        }
        authorizationService.assignDefaultRole(newUser.getId());
    }

    /**
     * 修改用户
     * @param user 用户
     */
    @Override
    public void updateUser(UserDTO user) {
        if (user.getId() == null || user.getId() <= 0) {
            throw new UserException("用户id格式错误！");
        }
        if (user.getId() == 1) {
            throw new UserException("对不起，您不能修改超级管理员的状态！", HttpStatus.FORBIDDEN);
        }
        Long userId = SecurityUtils.getUserId();
        if(userId.equals(user.getId())){
            throw new UserException("不能修改当前登录用户的状态");
        }
        //查询当前用户
        User byId = userEntityService.getById(user.getId());
        if(byId == null){
            throw new UserNotExistsException();
        }
        //检查用户名
        if (userEntityService.lambdaQuery()
                .eq(User::getUsername, user.getUsername().trim())
                .ne(User::getId, user.getId())
                .exists()) {
            throw new UserException("用户名已存在");
        }
        //检查邮箱
        if (userEntityService.lambdaQuery()
                .eq(User::getEmail, user.getEmail().trim())
                .ne(User::getId, user.getId())
                .exists()) {
            throw new UserException("此邮箱已被其他用户注册");
        }
        //修改用户不修改密码
        user.setPassword(null);
        User newUser = BeanUtil.copyProperties(user, User.class);
        boolean ok = userEntityService.updateById(newUser);
        if(!ok){
            throw new UserException("修改用户信息失败，请稍后再试");
        }
    }

}
