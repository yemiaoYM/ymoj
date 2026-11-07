package com.ymcloud.pojo.contract.judge;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 判题任务与结果的消息拓扑、服务鉴权及时间边界
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JudgeProtocol {

    public static final String EXCHANGE = "judge.worker.topic";
    public static final String TASK_QUEUE = "judge.worker.queue";
    public static final String RESULT_QUEUE = "judge.worker.result.queue";
    public static final String TASK_ROUTE = "judge";
    public static final String RESULT_ROUTE = "judge.result";
    public static final String RETRY_ROUTE = "judge.retry";
    public static final String DEAD_ROUTE = "judge.dead";
    public static final String SERVICE_TOKEN_HEADER = "X-Judge-Worker-Token";

    /**
     * 单次任务从提交到截止的最大协议时长，包含排队、编译和全部测试点
     */
    public static final long MAX_TASK_DURATION_MILLIS = 60 * 60 * 1000L;

    /**
     * 消费端允许提交时间领先本机的最大时钟容差
     */
    public static final long MAX_CLOCK_SKEW_MILLIS = 60000L;
}
