package com.ymcloud.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-03  17:30
 * @Description: 判题任务、结果与 SPJ 编译的 RabbitMQ 消息拓扑
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RabbitMQConstants {

    /**
     * 判题任务与结果共用的 Topic 交换机
     */
    public static final String JUDGE_EXCHANGE = "judge.worker.topic";

    /**
     * 正式判题和自测任务队列
     */
    public static final String JUDGE_QUEUE = "judge.worker.queue";

    /**
     * 判题进度与终态结果队列
     */
    public static final String JUDGE_RESULT_QUEUE = "judge.worker.result.queue";

    /**
     * 判题任务路由键
     */
    public static final String JUDGE_ROUTING_KEY = "judge";

    /**
     * 判题结果路由键
     */
    public static final String JUDGE_RESULT_ROUTING_KEY = "judge.result";

    /**
     * 延迟重试路由键
     */
    public static final String JUDGE_RETRY_ROUTING_KEY = "judge.retry";

    /**
     * 死信路由键
     */
    public static final String JUDGE_DEAD_ROUTING_KEY = "judge.dead";

    /**
     * SPJ 编译预检请求队列
     */
    public static final String SPJ_COMPILE_QUEUE = "judge.spj.compile.queue";
}
