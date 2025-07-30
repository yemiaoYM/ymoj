package com.ymcloud.common.exception.user;

/**
 * @PackageName: com.ymcloud.common.exception.user
 * @ClassName: UserPasswordRetryLimitExceedException
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  12:18
 * @Description: 用户错误最大次数异常类
 */
public class UserPasswordRetryLimitExceedException extends UserException {

    public UserPasswordRetryLimitExceedException(int maxRetryCount,int lockTime) {
        super("用户超过最大"+maxRetryCount+"次试错，请等待"+lockTime+"分钟后再试");
    }

}
