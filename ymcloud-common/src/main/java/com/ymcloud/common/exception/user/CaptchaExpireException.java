package com.ymcloud.common.exception.user;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.user
 * @ClassName: CaptchaExpireException
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  23:44
 * @Description: 验证码超时异常
 */
public class CaptchaExpireException extends ServiceException {

    public CaptchaExpireException() {
        super("验证码超时");
    }
}
