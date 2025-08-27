package com.ymcloud.judge.sandbox;

import com.ymcloud.judge.domain.SandBoxRequest;

/**
 * @PackageName: com.ymcloud.judge.sandbox
 * @ClassName: SandBoxService
 * @Author: Yemiao
 * @CreateTime: 2025-08-05  17:14
 * @Description: 沙箱服务
 */
public interface SandBoxService {

    /**
     * 编译
     * @param request 沙盒请求体
     */
    void compile(SandBoxRequest request);


    /**
     * 默认评测 需读取题目文件
     * @param request 沙盒请求体
     */
    void defaultJudge(SandBoxRequest request);

    /**
     * 自测评测
     * @param request 沙盒请求体
     */
    void testJudge(SandBoxRequest request);
}
