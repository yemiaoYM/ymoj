package com.ymcloud.core.security.context;

import org.springframework.security.core.Authentication;

/**
 * @PackageName: com.ymcloud.core.security.context
 * @ClassName: AuthenticationContextHolder
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  12:14
 * @Description: 身份验证信息
 */
public class AuthenticationContextHolder {

    private static final ThreadLocal<Authentication> contextHolder = new ThreadLocal<>();

    public static Authentication getContext()
    {
        return contextHolder.get();
    }

    public static void setContext(Authentication context)
    {
        contextHolder.set(context);
    }

    public static void clearContext()
    {
        contextHolder.remove();
    }

}
