package com.ymcloud.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  01:17
 * @Description: 评测常用变量
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JudgeConstants {

    /**
     * Worker 调用内部数据接口的身份请求头
     */
    public static final String SERVICE_TOKEN_HEADER = "X-Judge-Worker-Token";

    /**
     * 从提交到截止的最大任务时长，包含排队、编译和全部测试点，单位为毫秒
     */
    public static final long MAX_TASK_DURATION_MILLIS = 60 * 60 * 1000L;

    /**
     * SPJ 编译预检的消息有效期和调用方等待时限，单位为毫秒
     */
    public static final long SPJ_COMPILE_TIMEOUT_MILLIS = 45000;

    /**
     * 普通练习提交不属于任何比赛
     */
    public static final long PRACTICE_CONTEST_ID = 0L;
    /**
     * 评测策略
     */
    public static final Integer DEFAULT_JUDGE = 0;
    public static final Integer TEST_JUDGE = 1;
    /**
     * 评测模式
     */
    public static final Integer ACM = 0;
    public static final Integer OI = 1;
    /**
     * 本地评测机
     */
    public static final String LOCAL_JUDGE_SERVER = "LocalJudgeServer";
    /**
     * 编译脚本
     */
    public static final String COMPILE_SH="compile.sh";
    /**
     * 默认模式判题脚本
     */
    public static final String DEFAULT_RUN_SH="run_multi.sh";
    /**
     * 默认模式判题脚本
     */
    public static final String TEST_RUN_SH="run_test.sh";
    /**
     * 成功
     */
    public static final String SUCCESS="SUCCESS";
    /**
     * 失败
     */
    public static final String FAILURE="FAILURE";

}
