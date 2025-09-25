package com.ymcloud.core.security.domain;

import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
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
     * 用户信息缓存在redis中的唯一标识
     */
    private String token;

    /**
     * 登录时间
     */
    private LocalDateTime loginDateTime;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;

    /**
     * IP地址
     */
    private String ipaddr;

    /**
     * 登录地点
     */
    private String loginLocation;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 用户信息
     */
    private User user;

    /**
     * 用户角色信息
     */
    private List<String> roles;

    /**
     * 用户权限信息
     */
    private List<String> permissions;

    public LoginUser(User user) {
        this.user = user;
    }

    public LoginUser(User user, List<String> roles, List<String> permissions) {
        this.user = user;
        this.roles = roles;
        this.permissions = permissions;
    }

    /**
     * 返回权限列表
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String perm : permissions) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
        return authorities;
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
