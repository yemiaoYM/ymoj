package com.ymcloud.judge.domain;

import lombok.Data;

/**
 * @PackageName: com.ymcloud.judge.domain
 * @ClassName: SandBoxRequest
 * @Author: Yemiao
 * @CreateTime: 2025-08-05  17:19
 * @Description: 沙箱请求体
 */
@Data
public class SandBoxRequest {
    /**
     * 正式评测id
     */
    private Long submissionId;
    /**
     * 自测评测id
     */
    private String requestId;
    /**
     * 题目id
     */
    private Long problemId;
    /**
     * 时间限制
     */
    private Long timeLimit;
    /**
     * 内存限制
     */
    private Long memoryLimit;
    /**
     * 编译命令
     */
    private String compileCmd;
    /**
     * 运行命令
     */
    private String runCmd;
    /**
     * 执行环境
     */
    private String env;
}
