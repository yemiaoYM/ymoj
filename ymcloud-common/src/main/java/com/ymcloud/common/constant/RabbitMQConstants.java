package com.ymcloud.common.constant;

/**
 * @PackageName: com.ymcloud.common.constant
 * @ClassName: RabbitMQConstants
 * @Author: Yemiao
 * @CreateTime: 2025-08-03  17:30
 * @Description: 消息队列常用变量
 */
public class RabbitMQConstants {

    /**
     * 评测队列
     */
    public static final String JUDGE_QUEUE = "judge.queue";
    /**
     * 文件清理队列
     */
    public static final String FILE_CLEAN_QUEUE = "file.clean.queue";
    /**
     * 评测结束更新用户ac表队列
     */
    public static final String JUDGE_AC_UPDATE_QUEUE = "judge.ac.update.queue";


    /**
     * 评测交换机
     */
    public static final String JUDGE_EXCHANGE = "judge.topic";
    /**
     * 文件交换机
     */
    public static final String FILE_EXCHANGE = "file.topic";


    /**
     * 评测路由key
     */
    public static final String JUDGE_ROUTING_KEY = "judge";
    /**
     * 评测ac表更新路由key
     */
    public static final String JUDGE_AC_UPDATE_ROUTING_KEY = "judge.ac.update";
    /**
     * 文件路由key
     */
    public static final String FILE_CLEAN_ROUTING_KEY = "file.clean";


}
