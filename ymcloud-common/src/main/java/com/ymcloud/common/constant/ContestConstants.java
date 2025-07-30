package com.ymcloud.common.constant;

/**
 * @PackageName: com.ymcloud.common.constant
 * @ClassName: ContestConstants
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  01:05
 * @Description: 比赛常用变量
 */
public class ContestConstants {

    /**
     * 比赛状态
     */
    public static final String STATUS_NOT_STARTED = "未开始";
    public static final String STATUS_RUNNING = "进行中";
    public static final String STATUS_ENDED = "已结束";

    /**
     * 比赛模式
     */
    public static final int MODE_ACM = 0;
    public static final int MODE_OI = 1;
    public static final int MODE_IOI = 2;


    /**
     * 比赛权限
     */
    public static final int VISIBILITY_PUBLIC = 0;     // 公开
    public static final int VISIBILITY_PRIVATE = 1;    // 私有（进入需密码）
    public static final int VISIBILITY_PROTECTED = 2;  // 保护（提交需密码）

    /**
     * Rating
     */
    public static final int RATED = 1;

    /**
     * 是否开放排行榜
     */
    public static final int OPEN_RANK = 1;

    /**
     * 是否允许赛后提交
     */
    public static final int ALLOW_POST_SUBMIT = 1;
}
