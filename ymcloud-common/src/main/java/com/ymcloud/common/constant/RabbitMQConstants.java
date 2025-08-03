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
     * 评测交换机
     */
    public static final String JUDGE_EXCHANGE = "judge.topic";
    /**
     * 评测路由key
     */
    public static final String JUDGE_ROUTING_KEY = "judge";


}
