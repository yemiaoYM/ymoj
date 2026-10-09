package com.ymcloud.core.security.service;

import com.ymcloud.common.exception.user.UserException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
/**
 * @PackageName: com.ymcloud.core.security.service.impl
 * @ClassName: UserDetailsServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  12:45
 * @Description: 根据用户名加载用户详细信息
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private UserEntityService userEntityService;

    /**
     * 从数据库中加载用户
     * @param username 用户名
     * @return 封装loginUser对象
     * @throws UsernameNotFoundException 根据用户名查询不存在用户
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //1.从数据库中根据用户名查询用户
        User user = userEntityService.lambdaQuery()
                .eq(User::getUsername, username)
                .one();
        //2.校验用户
        if (user == null) {
            throw new UserException("该用户不存在");
        }
        if (UserStatus.DISABLE == user.getStatus()) {
            throw new UserException("该用户被停用");
        }
        //3.校验密码以及试错次数控制
        passwordService.validate(user);
        //4.查询角色和权限信息
        LoginUser loginUser = new LoginUser(user);
        authorizationService.loadAuthorities(loginUser);
        //5.返回UserDetails对象
        return loginUser;
    }
}
