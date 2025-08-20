package com.ymcloud.core.security.domain;

import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @PackageName: com.ymcloud.core.security.domain
 * @ClassName: LoginUser
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  12:25
 * @Description: 登录认证用户
 */
@Data
public class LoginUser implements UserDetails {

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * 用户信息缓存在redis中的唯一标识
     */
    private String token;

    /**
     * 用户信息
     */
    private User user;

    /**
     * 用户角色信息
     */
    private Set<String> roles;

    /**
     * 用户权限信息
     */
    private Set<String> permissions;

    public LoginUser(User user) {
        this.user = user;
    }

    /**
     * 返回权限列表
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /**
     * 获取密码
     */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * 获取用户名
     */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * 判断账户是否过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 判断账户是否没有锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 判断账户凭证（密码）是否未过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 判断账户是否可用
     */
    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.OK;
    }
}
