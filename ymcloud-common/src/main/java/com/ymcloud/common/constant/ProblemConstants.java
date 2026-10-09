package com.ymcloud.common.constant;

/**
 * @PackageName: com.ymcloud.common.constant
 * @ClassName: ProblemConstants
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  12:35
 * @Description: 题目常用变量
 */
public class ProblemConstants {

    /**
     * 可见公开
     */
    public final static int PUBLIC = 1;
    /**
     * 私有题目
     */
    public final static int PRIVATE = 2;
    /**
     * 比赛专用题目
     */
    public final static int CONTEST_PROBLEM = 3;

    /**
     * 题目测试样式展示
     */
    public final static Integer CASE_SHOW = 1;

    /**
     * 测试用例最大文件大小
     */
    public final static long MAX_TEST_CASE_FILE_SIZE = 50L * 1024 * 1024;

    /**
     * 本地题目展示ID前缀
     */
    public final static String LOCAL_PROBLEM_ID_PREFIX = "YMOJ-";

    /**
     * 本地题目初始题号
     */
    public final static long INITIAL_LOCAL_PROBLEM_NUMBER = 1000L;

}
