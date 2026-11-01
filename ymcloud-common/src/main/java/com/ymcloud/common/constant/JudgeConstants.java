package com.ymcloud.common.constant;

/**
 * @PackageName: com.ymcloud.common.constant
 * @ClassName: JudgeConstants
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  01:17
 * @Description: 评测常用变量
 */
public class JudgeConstants {
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
