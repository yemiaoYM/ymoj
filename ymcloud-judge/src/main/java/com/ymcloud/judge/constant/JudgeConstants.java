package com.ymcloud.judge.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: Worker 消息拓扑、数据访问请求头及执行时限
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JudgeConstants {

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

    /**
     * Worker 调用内部数据接口的身份请求头
     */
    public static final String SERVICE_TOKEN_HEADER = "X-Judge-Worker-Token";

    /**
     * 从提交到截止的最大任务时长，包含排队、编译和全部测试点，单位为毫秒
     */
    public static final long MAX_TASK_DURATION_MILLIS = 60 * 60 * 1000L;

    /**
     * 消息入口容许的最大时钟偏差，单位为毫秒
     */
    public static final long MAX_CLOCK_SKEW_MILLIS = 60000L;

    /**
     * SPJ 编译预检的消息有效期和调用方等待时限，单位为毫秒
     */
    public static final long SPJ_COMPILE_TIMEOUT_MILLIS = 45000;
}
