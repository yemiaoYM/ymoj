package com.ymcloud.judge.strategy;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.judge.domain.JudgeMessage;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @PackageName: com.ymcloud.judge.strategy
 * @ClassName: JudgeExecutor
 * @Author: Yemiao
 * @CreateTime: 2025-08-03  19:16
 * @Description: 执行判题
 */
@Component
@Slf4j
public class JudgeExecutor {

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private JudgeCaseEntityService judgeCaseEntityService;

    @Value("${ymcloud.file.base-dir}")
    private String BASE_DIR;

    @Value("${ymcloud.file.judge-sh}")
    private String JUDGE_SH;

    /**
     * 执行判题
     * @param message 判题信息
     */
    public void execute(JudgeMessage message){
        Judge judge = new Judge();
        judge.setId(message.getSubmissionId());
        try{
            //开始评测,先更新评测状态
            judge.setStatus(JudgeStatus.STATUS_JUDGING);
            judgeEntityService.updateById(judge);
            //获取语言配置
            Language language = message.getLanguage();
            //将用户代码保存为临时文件
            String dir= System.getProperty("user.dir")+FileUtil.FILE_SEPARATOR+BASE_DIR;
            String code = message.getCode();
            String userCodeFileDir= dir+FileUtil.FILE_SEPARATOR
                    +FileConstants.DIR_TMP+ FileUtil.FILE_SEPARATOR
                    +"submission_"+message.getSubmissionId();
            //files/tmp/submission_1
            String userCodeFileName = language.getFileName();
            //main.cpp
            String userCodeFilePath = userCodeFileDir+FileUtil.FILE_SEPARATOR+userCodeFileName;
            //files/tmp/submission_1/main.cpp
            //创建文件
            FileUtil.touch(userCodeFilePath);
            //将用户代码写入文件
            FileUtil.writeString(code,userCodeFilePath, StandardCharsets.UTF_8);
            //题目的输出输入文件
            //files/private/problem/problem_1
            //输入：1.in 输出1.out ...多组样例
            String problemCaseDir= dir+FileUtil.FILE_SEPARATOR
                    + FileConstants.DIR_PRIVATE + FileUtil.FILE_SEPARATOR
                    + FileConstants.SUB_DIR_PROBLEM + FileUtil.FILE_SEPARATOR
                    + "problem_" + message.getProblemId();
            //计算一下样例的个数in,out是成对的
            long caseCount = FileUtil.listFileNames(problemCaseDir).stream()
                    .filter(name->name.endsWith(".in")).count();
            //编译和运行指令
            String compileCmd=language.getCompileCmd();
            String runCmd=language.getRunCmd();
            //执行判题
            //docker的执行文件 多组为：judge_multi.sh 单例为：judge.sh
            //此处提交判题都为多组模式
            //files/private/docker
            String sh=JUDGE_SH +"judge1.sh";
            String cmd = String.format(
                    "bash %s %s %s \"%s\" \"%s\" %s",
                    sh,
                    message.getSubmissionId(), message.getProblemId(),
                    compileCmd, runCmd, language.getEnv()
            );
            String res = RuntimeUtil.execForStr(cmd);
            System.out.println(res);

            //编译错误compileError.out文件，运行错误文件error_i.out，
            //运行时间和内存文件time_i.out都生成在userCodeFileDir中
            String compileError=userCodeFileDir + FileUtil.FILE_SEPARATOR+ "compileError.out";
            //编译错误
            if(FileUtil.isNotEmpty(FileUtil.file(compileError))){
                judge.setStatus(JudgeStatus.STATUS_COMPILE_ERROR);
                judge.setErrorMessage(FileUtil.readUtf8String(compileError));
            }else{
                //编译成功
                //如果没有错误，则比较输出内容
                //执行用户代码生成的输出文件为：1.out等等 但是是保存在userCodeFileDir下的
                List<JudgeCase> judgeCaseList =new ArrayList<>();
                for(int i=1;i<=caseCount;i++){
                    String userOutFile=userCodeFileDir + FileUtil.FILE_SEPARATOR + i + ".out";
                    String acceptOutFile=problemCaseDir + FileUtil.FILE_SEPARATOR + i + ".out";
                    //time_1.out
                    String timeFile=userCodeFileDir + FileUtil.FILE_SEPARATOR+"time_"+i+".out";
                    //error_1.out
                    String errorOutFile=userCodeFileDir + FileUtil.FILE_SEPARATOR+"error_"+i+".out";
                    //构造评测样例实体
                    JudgeCase judgeCase=new JudgeCase();
                    judgeCase.setJudgeId(message.getSubmissionId());
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
                    if(time>message.getTimeLimit()){
                        //判断是否超时
                        judgeCase.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
                    }else if(memory>message.getMemoryLimit()){
                        //判断是否超出内存
                        judgeCase.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
                    }else{
                        //否则ac
                        judgeCase.setStatus(JudgeStatus.STATUS_ACCEPTED);
                    }
                    judgeCase.setTime(time);
                    judgeCase.setMemory(memory);
                    judgeCaseList.add(judgeCase);
                }
                //保存各个测试样例结果
                judgeCaseEntityService.saveBatch(judgeCaseList);
                judge.setStatus(JudgeStatus.STATUS_ACCEPTED);
                //所有测试样例成功才算ac
                for(JudgeCase judgeCase:judgeCaseList){
                    //如果有一个样例不为ac，那么当前评测结果就是这个评测样例的结果
                    if(judgeCase.getStatus()!=JudgeStatus.STATUS_ACCEPTED){
                        judge.setStatus(judgeCase.getStatus());
                        break;
                    }
                }
            }
            //更新评测状态
            judgeEntityService.updateById(judge);
        }catch (ServiceException e){
            log.info("评测业务异常：{}",e.getMessage());
            judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
            judge.setErrorMessage(e.getMessage());
            judgeEntityService.updateById(judge);
        }catch (Exception e){
            log.error("评测系统异常：{}",e.getMessage());
            judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
            judge.setErrorMessage(e.getMessage());
            judgeEntityService.updateById(judge);
        }
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











