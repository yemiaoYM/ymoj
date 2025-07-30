package com.ymcloud.common.exception.user;

/**
 * @PackageName: com.ymcloud.common.exception.user
 * @ClassName: UserPasswordNotMatchException
 * @Author: Yemiao
 * @CreateTime: 2025-07-19  00:36
 * @Description: 密码错误或者密码长度不匹配异常
 */
public class UserPasswordNotMatchException extends UserException {

    public UserPasswordNotMatchException() {
        super("用户密码不匹配");
    }

    public UserPasswordNotMatchException(String message) {
        super(message);
    }

}
