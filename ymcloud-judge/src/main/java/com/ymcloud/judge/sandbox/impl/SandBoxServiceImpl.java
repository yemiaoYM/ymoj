package com.ymcloud.judge.sandbox.impl;

import cn.hutool.core.util.RuntimeUtil;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.judge.domain.SandBoxRequest;
import com.ymcloud.judge.sandbox.SandBoxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @PackageName: com.ymcloud.judge.sandbox.impl
 * @ClassName: SandBoxServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-05  17:15
 * @Description: 沙箱具体实现
 */
@Component
@Slf4j
public class SandBoxServiceImpl implements SandBoxService {

    /**
     * 文件基础路径
     */
    @Value("${ymcloud.file.base-path}")
    private String BASE_DIR;

    /**
     * 判题脚本路径
     */
    @Value("${ymcloud.file.judge-sh}")
    private String SH_DIR;

    /**
     * 编译
     * @param request 沙盒请求体
     */
    @Override
    public void compile(SandBoxRequest request){
        log.info("沙盒开始执行编译...");
        //1.提取脚本参数
        String submissionId = request.getSubmissionId().toString();
        String compileCmd = request.getCompileCmd();
        String imageName = request.getEnv();
        //2.构建命令
        String cmd = String.format(
                "bash %s %s \"%s\" %s %s",
                SH_DIR + JudgeConstants.COMPILE_SH,
                submissionId,
                compileCmd,
                imageName,
                BASE_DIR
        );
        //3.执行脚本
        String result = RuntimeUtil.execForStr(cmd);
        log.info("执行结果：{}",result);
    }

    /**
     * 默认评测 需读取题目文件
     * @param request 沙盒请求体
     */
    @Override
    public void defaultJudge(SandBoxRequest request){
        log.info("沙盒开始执行默认评测运行...");
        //1.提取脚本参数
        String submissionId = request.getSubmissionId().toString();
        String problemId = request.getProblemId().toString();
        String runCmd = request.getRunCmd();
        String imageName = request.getEnv();
        //2.构建命令
        String cmd = String.format(
                "bash %s %s %s \"%s\" %s %s",
                SH_DIR + JudgeConstants.DEFAULT_RUN_SH,
                submissionId,
                problemId,
                runCmd,
                imageName,
                BASE_DIR
        );
        //3.执行脚本
        String result = RuntimeUtil.execForStr(cmd);
        log.info("执行结果：{}",result);
    }


}
