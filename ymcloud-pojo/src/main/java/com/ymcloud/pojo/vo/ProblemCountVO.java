package com.ymcloud.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ProblemCountVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  00:16
 * @Description: 题目提交状态统计
 */
@Data
public class ProblemCountVO implements Serializable {
    private final static long serialVersionUID = 1L;

    /**
     * 题目唯一标识
     */
    private String problemId;
    /**
     * 总提交数
     */
    private int total;
    /**
     * 通过
     */
    private int ac;
    /**
     * 空间超限
     */
    private int mle;
    /**
     * 时间超限
     */
    private int tle;
    /**
     * 运行错误
     */
    private int re;
    /**
     * 格式错误
     */
    private int pe;
    /**
     * 编译错误
     */
    private int ce;
    /**
     * 答案错误
     */
    private int wa;
    /**
     * 系统错误
     */
    private int se;
    /**
     * 部分通过，OI题目
     */
    private int pa;
}
