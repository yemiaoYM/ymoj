package com.ymcloud.common.exception.user;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.user
 * @ClassName: CaptchaException
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  23:45
 * @Description: 验证码错误异常
 */
public class CaptchaException extends ServiceException {

    public CaptchaException() {
        super("验证码错误");
    }

    public CaptchaException(String message) {
        super(message);
    }

}
