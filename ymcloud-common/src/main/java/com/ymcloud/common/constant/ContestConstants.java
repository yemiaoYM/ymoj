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
     * 原创比赛没有源比赛
     * 克隆比赛保存实际源比赛 ID
     */
    public static final long ORIGINAL_SOURCE_ID = 0L;

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
    public static final int VISIBILITY_PASSWORD = 1;  // 密码比赛
    public static final int VISIBILITY_PRIVATE = 2;   // 仅创建者及普通管理员以上可访问

    /**
     * Rating
     */
    public static final int RATED = 1;

    /**
     * 是否开放排行榜
     */
    public static final int OPEN_RANK = 1;

}
