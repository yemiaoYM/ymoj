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
     * 用户缓存资源 redis key
     */
    public static final String CACHE_NAME = "cache";
    /**
     * 标签列表 redis key
     */
    public static final String TAG_LIST_KEY = "tag_list";

    /**
     * 公开轮播列表；完整 Redis 键为 cache:home_carousel，变更提交后失效。
     */
    public static final String HOME_CAROUSEL_KEY = "home_carousel";

    /**
     * 用户ac排名列表 redis key
     */
    public static final String USER_AC_RANK_KEY = "user_ac_rank";
    /**
     * 比赛缓存 redis key
     */
    public static final String CONTEST_KEY = "contest";
    /**
     * 题目缓存 redis key
     */
    public static final String PROBLEM_KEY = "problem";

    /**
     * 评测状态 redis key
     */
    public static final String JUDGE_KEY = "judge:";

    /**
     * 评测状态 redis key
     */
    public static final String FILE_KEY = "file";

    /**
     * 邮箱验证码防重提交 redis key
     */
    public static final String EMAIL_CODE_REPEAT_SUBMIT_KEY = "email_code_repeat_submit:";

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
