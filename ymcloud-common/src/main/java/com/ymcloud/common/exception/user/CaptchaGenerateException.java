package com.ymcloud.common.exception.user;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.user
 * @ClassName: CaptchaGenerateException
 * @Author: Yemiao
 * @CreateTime: 2025-07-21  19:47
 * @Description: 验证码生成失败异常类
 */
public class CaptchaGenerateException extends ServiceException {

    public CaptchaGenerateException() {
        super("验证码生成失败");
    }
}
