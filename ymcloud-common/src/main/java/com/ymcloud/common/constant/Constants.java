package com.ymcloud.common.constant;

import io.jsonwebtoken.Claims;

/**
 * @PackageName: com.ymcloud.common.constant
 * @ClassName: Constants
 * @Author: Yemiao
 * @CreateTime: 2025-07-17  21:43
 * @Description: 通用常量信息
 */
public class Constants {

    /**
     * 令牌
     */
    public static final String TOKEN = "token";

    /**
     * 令牌前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 令牌前缀
     */
    public static final String LOGIN_USER_KEY = "login_user_key";

    /**
     * 用户名称
     */
    public static final String JWT_USERNAME = Claims.SUBJECT;

    /**
     * 验证码有效期（分钟）
     */
    public static final Integer CAPTCHA_EXPIRATION = 1;

    /**
     * 自动识别json对象白名单配置（仅允许解析的包名，范围越小越安全）
     */
    public static final String[] JSON_WHITELIST_STR = { "org.springframework", "com.ymcloud" };

}
