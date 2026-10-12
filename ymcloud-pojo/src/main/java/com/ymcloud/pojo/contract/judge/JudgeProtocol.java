package com.ymcloud.pojo.contract.judge;

/**
 * 第二代独立 Worker 消息协议。新队列与旧 Web 内置消费者隔离。
 */
public final class JudgeProtocol {

    public static final int SCHEMA_VERSION = 2;
    public static final String EXCHANGE = "judge.worker-v2.topic";
    public static final String TASK_QUEUE = "judge.worker-v2.queue";
    public static final String RESULT_QUEUE = "judge.worker-v2.result.queue";
    public static final String TASK_ROUTE = "judge";
    public static final String RESULT_ROUTE = "judge.result";
    public static final String RETRY_ROUTE = "judge.retry";
    public static final String DEAD_ROUTE = "judge.dead";
    public static final String SERVICE_TOKEN_HEADER = "X-Judge-Worker-Token";

    private JudgeProtocol() {
    }
}
