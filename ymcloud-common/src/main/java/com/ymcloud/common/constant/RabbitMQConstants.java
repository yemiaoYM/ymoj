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
     * 评测结束文件清理队列
     */
    public static final String JUDGE_FILE_CLEAN_QUEUE = "judge.file.clean.queue";
    /**
     * 评测结束更新用户ac表队列
     */
    public static final String JUDGE_AC_UPDATE_QUEUE = "judge.ac.update.queue";
    /**
     * 评测结束通知前端队列
     */
    public static final String JUDGE_WS_NOTIFY_QUEUE = "judge.ws.notify.queue";
    /**
     * 评测交换机
     */
    public static final String JUDGE_EXCHANGE = "judge.topic";
    /**
     * 评测交换机
     */
    public static final String JUDGE_RESULT_EXCHANGE = "judge.result.topic";
    /**
     * 评测路由key
     */
    public static final String JUDGE_ROUTING_KEY = "judge";
    /**
     * 评测路由key
     */
    public static final String JUDGE_RESULT_ROUTING_KEY = "judge.result";


}
