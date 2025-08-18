package com.ymcloud.judge.strategy;

import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.judge.SandBoxException;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.judge.domain.JudgeResponse;
import com.ymcloud.judge.domain.SandBoxRequest;
import com.ymcloud.judge.sandbox.SandBoxService;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        try {
            //沙箱进行编译
            sandBoxService.compile(request);
            //构造相应实体
            JudgeResponse response = new JudgeResponse();
            String errorFile = BASE_DIR + "/"
                    + FileConstants.DIR_TMP + "/"
                    + "submission_" + request.getSubmissionId() + "/"
                    + "compile_error.out";
            if (!FileUtils.empty(errorFile)) {
                //编译失败
                String errorMessage = FileUtils.readFileString(errorFile);
                response.setErrorMessage(errorMessage);
                return response;
            }
            return response;
        }catch (SandBoxException | FileException e) {
            throw new JudgeException(e.getMessage(), e);
        } catch (Exception e) {
            throw new JudgeException("编译处理逻辑异常",e);
        }
    }

    /**
     * 默认模式执行代码
     * @param request 评测请求体
     * @return 评测结果
     */
    public JudgeResponse defaultJudge(SandBoxRequest request) {
        try {
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
            String problemCaseDir = BASE_DIR + "/"
                    + FileConstants.DIR_PRIVATE + "/"
                    + FileConstants.SUB_DIR_PROBLEM + "/"
                    + "problem_" + request.getProblemId();
            //计算一下样例的个数in,out是成对的
            long caseCount = FileUtils.countFileBySuffix(problemCaseDir, ".in");
            //提交文件路径
            String userCodeFileDir = BASE_DIR + "/"
                    + FileConstants.DIR_TMP + "/"
                    + "submission_" + request.getSubmissionId();
            //执行用户代码生成的输出文件为：1.out等等 但是是保存在userCodeFileDir下的
            List<JudgeCase> judgeCaseList = new ArrayList<>();
            for (int i = 1; i <= caseCount; i++) {
                String userOutFile = userCodeFileDir + "/" + i + ".out";
                String acceptOutFile = problemCaseDir + "/" + i + ".out";
                //time_1.out
                String timeFile = userCodeFileDir + "/" + "time_" + i + ".out";
                //error_1.out
                String errorOutFile = userCodeFileDir + "/" + "error_" + i + ".out";
                //构造评测样例实体
                JudgeCase judgeCase = new JudgeCase();
                judgeCase.setJudgeId(request.getSubmissionId());
                judgeCase.setCaseIndex(i);
                //判断是否运行成功
                if(!FileUtils.empty(errorOutFile)){
                    judgeCase.setStatus(JudgeStatus.STATUS_RUNTIME_ERROR);
                    judgeCase.setErrorMessage(FileUtils.readFileString(errorOutFile));
                    judgeCaseList.add(judgeCase);
                    continue;
                }
                //正常运行才获取运行时间和内存
                Map<String, Long> map = parseTimeAndMemory(timeFile);
                Long time = map.get("time");
                Long memory = map.get("memory");
                //判断答案是否正确
                boolean b = compareOutput(acceptOutFile, userOutFile);
                if (!b) {
                    //答案不正确直接wa
                    judgeCase.setStatus(JudgeStatus.STATUS_WRONG_ANSWER);
                    //对于错误信息有待完善处理
                    //judgeCase.setErrorMessage(FileUtils.readFileString(userOutFile));
                } else if (time > request.getTimeLimit()) {
                    //判断是否超时
                    judgeCase.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
                } else if (memory > request.getMemoryLimit()) {
                    //判断是否超出内存
                    judgeCase.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
                } else {
                    //否则ac
                    judgeCase.setStatus(JudgeStatus.STATUS_ACCEPTED);
                }
                //单个测试样例的时间和内存
                judgeCase.setTime(time);
                judgeCase.setMemory(memory);
                //评测所用时间和内存是测试样例中的最大值
                response.setTime(Math.max(time, response.getTime()));
                response.setMemory(Math.max(memory, response.getMemory()));
                judgeCaseList.add(judgeCase);
            }
            response.setJudgeCases(judgeCaseList);
            return response;
        } catch (SandBoxException | FileException e) {
            throw new JudgeException(e.getMessage(),e);
        } catch (Exception e) {
            throw new JudgeException("默认模式执行代码逻辑异常",e);
        }
    }

    public JudgeResponse testJudge(SandBoxRequest request) {
        try {
            //沙箱进行执行
            sandBoxService.testJudge(request);
            //提交文件路径
            String userCodeFileDir = BASE_DIR + "/"
                    + FileConstants.DIR_TMP + "/"
                    + "submission_" + request.getSubmissionId();
            //构造相应实体
            JudgeResponse response = new JudgeResponse();
            response.setTime(0L);
            response.setMemory(0L);
            //输出文件
            String userOutFile = userCodeFileDir + "/output.out";
            String timeFile = userCodeFileDir + "/time.out";
            String errorOutFile = userCodeFileDir + "/error.out";
            //先判断是否运行成功
            if(!FileUtils.empty(errorOutFile)){
                response.setStatus(JudgeStatus.STATUS_RUNTIME_ERROR);
                response.setErrorMessage(FileUtils.readFileString(errorOutFile));
                return response;
            }
            //正常运行才获取运行时间和内存
            Map<String, Long> map = parseTimeAndMemory(timeFile);
            Long time = map.get("time");
            Long memory = map.get("memory");
            //判断状态
            if(time > request.getTimeLimit()){
                response.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
            }else if(memory > request.getMemoryLimit()){
                response.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
            }else{
                response.setStatus(JudgeStatus.STATUS_ACCEPTED);
            }
            response.setTime(time);
            response.setMemory(memory);
            //程序输出内容
            response.setOutput(FileUtils.readFileString(userOutFile));
            return response;
        } catch (SandBoxException | FileException e) {
            throw new JudgeException(e.getMessage(),e);
        } catch (Exception e) {
            throw new JudgeException("自测模式执行代码逻辑异常",e);
        }
    }

    /**
     * 从文件中解析运行时间和运行内存
     * @param timeFile 文件名
     * @return 时间和内存
     */
    private Map<String,Long> parseTimeAndMemory(String timeFile){
        Map<String,Long> mp = new HashMap<>();
        mp.put("time", 0L);
        mp.put("memory", 0L);
        //从文件中获取信息
        List<String> timeMsg = FileUtils.readFileLines(timeFile);
        for (String line : timeMsg) {
            if (line.startsWith("TIME:")) {
                double seconds = Double.parseDouble(line.substring(5).trim());
                mp.put("time", Math.round(seconds * 1000));
            } else if (line.startsWith("MEM:")) {
                mp.put("memory", Long.parseLong(line.substring(4).trim()));
            }
        }
        return mp;
    }

    /**
     * 比较标准输出和用户输出（宽松模式）
     * @param standardFile 标准输出文件
     * @param userFile 用户输出文件
     * @return true 表示匹配
     */
    private boolean compareOutput(String standardFile, String userFile) {
        List<String> standardLines = FileUtils.readFileLines(standardFile);
        List<String> userLines = FileUtils.readFileLines(userFile);
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
