package com.ymcloud.common.exception.user;

/**
 * @PackageName: com.ymcloud.common.exception.user
 * @ClassName: UserNotExistsException
 * @Author: Yemiao
 * @CreateTime: 2025-07-19  00:13
 * @Description: 用户不存在异常
 */
public class UserNotExistsException extends UserException {

    public UserNotExistsException() {
        super("用户不存在");
    }

}
