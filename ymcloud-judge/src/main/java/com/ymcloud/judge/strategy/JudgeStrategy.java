package com.ymcloud.judge.strategy;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.judge.domain.JudgeResponse;
import com.ymcloud.judge.domain.SandBoxRequest;
import com.ymcloud.judge.sandbox.SandBoxService;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @PackageName: com.ymcloud.judge.strategy
 * @ClassName: JudgeStrategy
 * @Author: Yemiao
 * @CreateTime: 2025-08-05  18:28
 * @Description: 评测策略
 */
@Component
@Slf4j
public class JudgeStrategy {

    @Autowired
    private SandBoxService sandBoxService;

    @Value("${ymcloud.file.base-dir}")
    private String BASE_DIR;

    /**
     * 编译
     * @param request 评测请求体
     * @return 评测结果
     */
    public JudgeResponse compile(SandBoxRequest request) {
        //沙箱进行编译
        sandBoxService.compile(request);
        //构造相应实体
        JudgeResponse response = new JudgeResponse();
        String errorFile = BASE_DIR+"/"+ FileConstants.DIR_TMP+"/"
                +"submission_"+request.getSubmissionId()+"/"+"compile_error.out";
        File file = FileUtil.file(errorFile);
        if(FileUtil.isNotEmpty(file)){
            //编译失败
            String errorMessage = FileUtil.readUtf8String(file);
            response.setErrorMessage(errorMessage);
            return response;
        }
        return response;
    }

    /**
     * 默认模式执行代码
     * @param request 评测请求体
     * @return 评测结果
     */
    public JudgeResponse defaultJudge(SandBoxRequest request) {
        //沙箱进行执行
        sandBoxService.defaultJudge(request);
        //构造相应实体
        JudgeResponse response = new JudgeResponse();
        response.setTime(0L);
        response.setMemory(0L);
        //判断各个评测点结果
        //题目的输出输入文件
        //files/private/problem/problem_1
        //输入：1.in 输出1.out ...多组样例
        String problemCaseDir= BASE_DIR+"/"+FileConstants.DIR_PRIVATE+"/"
                + FileConstants.SUB_DIR_PROBLEM + "/"
                + "problem_" + request.getProblemId();
        //计算一下样例的个数in,out是成对的
        long caseCount = FileUtil.listFileNames(problemCaseDir).stream()
                .filter(name->name.endsWith(".in")).count();
        //提交文件路径
        String userCodeFileDir = BASE_DIR+"/"+ FileConstants.DIR_TMP+"/"
                +"submission_"+request.getSubmissionId();
        //执行用户代码生成的输出文件为：1.out等等 但是是保存在userCodeFileDir下的
        List<JudgeCase> judgeCaseList =new ArrayList<>();
        for(int i=1;i<=caseCount;i++){
            String userOutFile=userCodeFileDir+"/"+i+".out";
            String acceptOutFile = problemCaseDir+"/"+i+".out";
            //time_1.out
            String timeFile=userCodeFileDir+"/"+"time_"+i+".out";
            //error_1.out
            String errorOutFile=userCodeFileDir+"/"+"error_"+i+".out";
            //构造评测样例实体
            JudgeCase judgeCase=new JudgeCase();
            judgeCase.setJudgeId(request.getSubmissionId());
            judgeCase.setCaseIndex(i);
            //判断是否运行成功
            if(FileUtil.isNotEmpty(FileUtil.file(errorOutFile))){
                judgeCase.setStatus(JudgeStatus.STATUS_RUNTIME_ERROR);
                judgeCase.setErrorMessage(FileUtil.readUtf8String(errorOutFile));
                judgeCaseList.add(judgeCase);
                continue;
            }
            //判断答案是否正确
            boolean b = compareOutput(acceptOutFile, userOutFile);
            if(!b){
                //说明wa
                judgeCase.setStatus(JudgeStatus.STATUS_WRONG_ANSWER);
                judgeCase.setErrorMessage(FileUtil.readUtf8String(userOutFile));
            }
            //只有正常运行才获取运行时间和内存
            List<String> timeMsg = FileUtil.readUtf8Lines(timeFile);
            //解析时间和内存
            long time=0L;
            long memory=0L;
            for(String line:timeMsg){
                if (line.startsWith("TIME:")) {
                    double seconds = Double.parseDouble(line.substring(5).trim());
                    time = Math.round(seconds * 1000);
                } else if (line.startsWith("MEM:")) {
                    memory = Long.parseLong(line.substring(4).trim());
                }
            }
            //判断剩下的状态
            if(time> request.getTimeLimit()){
                //判断是否超时
                judgeCase.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
            }else if(memory>request.getMemoryLimit()){
                //判断是否超出内存
                judgeCase.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
            }else{
                //否则ac
                judgeCase.setStatus(JudgeStatus.STATUS_ACCEPTED);
            }
            //单个测试样例的时间和内存
            judgeCase.setTime(time);
            judgeCase.setMemory(memory);
            //评测所用时间和内存是测试样例中的最大值
            response.setTime(Math.max(time,response.getTime()));
            response.setMemory(Math.max(memory,response.getMemory()));
            judgeCaseList.add(judgeCase);
        }
        response.setJudgeCases(judgeCaseList);
        return response;
    }


    /**
     * 比较标准输出和用户输出（宽松模式）
     * @param standardFile 标准输出文件
     * @param userFile 用户输出文件
     * @return true 表示匹配
     */
    public static boolean compareOutput(String standardFile, String userFile) {
        List<String> standardLines = FileUtil.readUtf8Lines(standardFile);
        List<String> userLines = FileUtil.readUtf8Lines(userFile);
        // 去掉行尾空格
        standardLines.replaceAll(StrUtil::trimEnd);
        userLines.replaceAll(StrUtil::trimEnd);
        // 去掉末尾空行
        while (!standardLines.isEmpty() && StrUtil.isBlank(standardLines.get(standardLines.size() - 1))) {
            standardLines.remove(standardLines.size() - 1);
        }
        while (!userLines.isEmpty() && StrUtil.isBlank(userLines.get(userLines.size() - 1))) {
            userLines.remove(userLines.size() - 1);
        }
        return standardLines.equals(userLines);
    }

}
