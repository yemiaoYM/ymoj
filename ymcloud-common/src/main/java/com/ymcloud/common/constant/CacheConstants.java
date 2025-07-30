package com.ymcloud.common.constant;

/**
 * @PackageName: com.ymcloud.common.constant
 * @ClassName: CacheConstants
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  23:16
 * @Description: 缓存key常量
 */
public class CacheConstants {

    /**
     * 标签列表 redis key
     */
    public static final String TAG_LIST_KEY = "cacheTag";

    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";
}
