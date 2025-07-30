package com.ymcloud.common.exception.user;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.user
 * @ClassName: UserException
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  12:37
 * @Description: 用户相关异常
 */
public class UserException extends ServiceException {

    public UserException(){
        super("用户操作异常");
    }

    public UserException(String message) {
        super(message);
    }

    public UserException(String message, Integer code) {
        super(message, code);
    }

}
