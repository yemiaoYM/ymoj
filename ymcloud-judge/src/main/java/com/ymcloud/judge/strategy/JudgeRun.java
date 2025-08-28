package com.ymcloud.judge.strategy;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.ymcloud.common.constant.*;
import com.ymcloud.common.exception.judge.CompileException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.judge.SandBoxException;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.domain.*;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.judge.strategy
 * @ClassName: JudgeRun
 * @Author: Yemiao
 * @CreateTime: 2025-08-05  18:29
 * @Description: 评测逻辑处理
 */
@Component
@Slf4j
public class JudgeRun {

    @Autowired
    private DockerClient dockerClient;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private JudgeCaseEntityService judgeCaseEntityService;

    @Value("${ymcloud.file.base-dir}")
    private String BASE_DIR;

    @Value("${ymcloud.docker.host-working-dir}")
    private String hostWorkingDir;
    @Value("${ymcloud.docker.container-working-dir}")
    private String containerWorkingDir;


    /**
     * 执行判题
     * @param message 判题信息
     */
    public void execute(JudgeMessage message){
        log.info("JudgeRun评测逻辑处理...");
        //判断评测策略
        if (message.getStrategy().equals(JudgeConstants.TEST_JUDGE)){
            testJudge(message);
        }
        else if(message.getStrategy().equals(JudgeConstants.DEFAULT_JUDGE)){
            defaultJudge(message);
        }
    }

    /**
     * 自测评测
     */
    private void testJudge(JudgeMessage message){
        //自测
        //提交文件路径
        String submissionDir = BASE_DIR + "/" +
                FileConstants.DIR_TMP + "/"
                + "submission_" + message.getSubmissionId();
        String key = CacheConstants.JUDGE_KEY + message.getSubmissionId();
        TestJudge judge = redisCache.getCacheObject(key);
        try{
            //先更新状态
            judge.setStatus(JudgeStatus.STATUS_JUDGING);
            redisCache.setCacheObject(key, judge, 3, TimeUnit.MINUTES);
            //获取语言配置
            Language language = message.getLanguageConfig();
            //创建用户代码文件并写入
            String userCodeFile = submissionDir + "/" + language.getFileName();
            FileUtils.writeFile(userCodeFile,message.getCode());
            //创建用户输入文件并写入
            String userInputFile = submissionDir + "/" + "input.in";
            FileUtils.writeFile(userInputFile,message.getInput());
            //构造沙箱请求体
            SandBoxRequest request = new SandBoxRequest();
            request.setSubmissionId(message.getSubmissionId());
            request.setTimeLimit(1000L);
            request.setMemoryLimit(65536L);
            request.setCompileCmd(language.getCompileCmd());
            request.setRunCmd(language.getRunCmd());
            request.setEnv(language.getEnv());
            //编译是否成功
            boolean compile = true;
            //判断是否需要编译
            if(language.getType().equals(LanguageConstants.COMPILE)){
                //需要编译
                JudgeResponse response=new JudgeResponse();
                if(StrUtil.isNotBlank(response.getErrorMessage())){
                    //有错误信息表示编译失败
                    judge.setStatus(JudgeStatus.STATUS_COMPILE_ERROR);
                    judge.setErrorMessage(response.getErrorMessage());
                    redisCache.setCacheObject(key, judge, 3, TimeUnit.MINUTES);
                    compile = false;
                }
            }
            //不需要编译或者编译成功
            if(compile) {
                //c,c++为默认一倍时间和空间，其他语言两倍
                if (!language.getFileName().endsWith(".cpp") || !language.getFileName().endsWith(".c")) {
                    request.setTimeLimit(request.getTimeLimit() * 2);
                    request.setMemoryLimit(request.getMemoryLimit() * 2);
                }
                //进行执行代码
                JudgeResponse response = new JudgeResponse();
                //设置运行时间和内存
                judge.setStatus(response.getStatus());
                judge.setTime(response.getTime());
                judge.setMemory(response.getMemory());
                judge.setOutput(response.getOutput());
                judge.setErrorMessage(response.getErrorMessage());
                redisCache.setCacheObject(key, judge, 3, TimeUnit.MINUTES);
            }
            //评测结束删除提交文件
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.FILE_EXCHANGE,
                    RabbitMQConstants.FILE_CLEAN_ROUTING_KEY,
                    submissionDir);
        } catch (Exception e){
            log.error("自测评测系统异常",e);
            judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
            judge.setErrorMessage("评测失败，请联系管理员");
            redisCache.setCacheObject(key, judge, 3, TimeUnit.MINUTES);
        }
    }

    /**
     * 默认模式评测
     */
    private void defaultJudge(JudgeMessage message){
        //默认评测都会更新数据库 查询当前评测信息
        //不放在try/catch中为了 连评测记录都拿不到，说明评测记录中途删除或者数据库加载失败
        //进行回滚 重试3次失败后到发送到error.queue，人工介入
        Judge judge = judgeEntityService.getById(message.getSubmissionId());
        if(judge == null){
            throw new JudgeException("评测记录不存在，评测失败");
        }
        CodeExecContainer container = null;
        try {
            //获取语言配置
            Language language = message.getLanguageConfig();
            //本地文件路径
            String hostFilePath = hostWorkingDir + "/" + IdUtil.simpleUUID();
            //从工厂中创建容器
            CodeExecContainerFactory factory = new CodeExecContainerFactory(
                    dockerClient, hostFilePath, containerWorkingDir, message.getMemoryLimit(), language.getEnv());
            container = factory.createDockerContainer();
            //启动容器
            container.start();
            //根据语言选择沙箱判题策略
            SandBoxFactory sandBoxFactory = new SandBoxFactory(language.getName());
            SandBox sandBox = sandBoxFactory.createSandBox(container);
            //保存用户代码
            sandBox.saveCode(message.getCode());
            //编译
            sandBox.compile(container);
            //获取所有测试用例
            List<TestCase> testCases=getAllTestCases(message.getProblemId());
            for(TestCase testCase:testCases){
                System.out.println("[第"+testCase.getId()+"个]测试样例开始评测...");
                CodeRunResult execute = sandBox.execute(container, testCase.getInput()+"\n");
                System.out.println("[第"+testCase.getId()+"个]测试样例的结果为："+execute);
            }
        }catch (CompileException e){
            judge.setStatus(JudgeStatus.STATUS_COMPILE_ERROR);
            judge.setErrorMessage(e.getMessage());
            judgeEntityService.updateById(judge);
        } catch (SandBoxException e) {
            log.error("默认评测沙箱异常",e);
            judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
            judge.setErrorMessage("对不起，沙箱运行异常，请联系管理员");
            judgeEntityService.updateById(judge);
        } catch (Exception e){
            log.error("默认评测系统异常",e);
            judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
            judge.setErrorMessage("评测失败，请联系管理员");
            judgeEntityService.updateById(judge);
        }finally {
            //删除容器
//            if (container != null) {
//                container.remove(true);
//            }
        }
    }


//    private void defaultJudge(JudgeMessage message){
//        //默认评测都会更新数据库 查询当前评测信息
//        //不放在try/catch中为了 连评测记录都拿不到，说明评测记录中途删除或者数据库加载失败
//        //进行回滚 重试3次失败后到发送到error.queue，人工介入
//        Judge judge = judgeEntityService.getById(message.getSubmissionId());
//        if(judge == null){
//            throw new JudgeException("评测记录不存在，评测失败");
//        }
//        //提交文件路径
//        String submissionDir = BASE_DIR + "/" +
//                FileConstants.DIR_TMP + "/"
//                + "submission_" + message.getSubmissionId();
//        try{
//            //获取语言配置
//            Language language = message.getLanguageConfig();
//            //创建用户代码文件并写入
//            //files/tmp/submission_1/main.cpp
//            String userCodeFile = submissionDir + "/" + language.getFileName();
//            FileUtils.writeFile(userCodeFile,message.getCode());
//            //构造沙箱请求体
//            SandBoxRequest request = new SandBoxRequest();
//            request.setSubmissionId(message.getSubmissionId());
//            request.setProblemId(message.getProblemId());
//            request.setTimeLimit(message.getTimeLimit());
//            request.setMemoryLimit(message.getMemoryLimit());
//            request.setCompileCmd(language.getCompileCmd());
//            request.setRunCmd(language.getRunCmd());
//            request.setEnv(language.getEnv());
//            //编译是否成功
//            boolean compile = true;
//            //判断是否需要编译
//            if(language.getType().equals(LanguageConstants.COMPILE)){
//                //先更新编译状态
//                judge.setStatus(JudgeStatus.STATUS_COMPILING);
//                judgeEntityService.updateById(judge);
//                //需要编译
//                JudgeResponse response=judgeStrategy.compile(request);
//                if(StrUtil.isNotBlank(response.getErrorMessage())){
//                    //有错误信息表示编译失败
//                    judge.setStatus(JudgeStatus.STATUS_COMPILE_ERROR);
//                    judge.setErrorMessage(response.getErrorMessage());
//                    judgeEntityService.updateById(judge);
//                    compile = false;
//                }
//            }
//            //不需要编译或者编译成功
//            if(compile) {
//                //进行评测
//                judge.setStatus(JudgeStatus.STATUS_JUDGING);
//                judgeEntityService.updateById(judge);
//                //c,c++为默认一倍时间和空间，其他语言两倍
//                if (!language.getFileName().endsWith(".cpp") || !language.getFileName().endsWith(".c")) {
//                    request.setTimeLimit(message.getTimeLimit() * 2);
//                    request.setMemoryLimit(message.getMemoryLimit() * 2);
//                }
//                //进行执行代码
//                JudgeResponse response = judgeStrategy.defaultJudge(request);
//                //设置运行时间和内存
//                judge.setTime(response.getTime());
//                judge.setMemory(response.getMemory());
//                //根据评测模式进行评测结果设置
//                List<JudgeCase> judgeCases = response.getJudgeCases();
//                //目前处除了io其他都按照acm模式评测
//                if (message.getMode().equals(JudgeConstants.OI)) {
//                    int total = judgeCases.size();
//                    long ac = judgeCases.stream()
//                            .filter(c -> c.getStatus() == JudgeStatus.STATUS_ACCEPTED)
//                            .count();
//                    //分数=(ac/total)*100 向上取整
//                    int score = (int) Math.ceil(ac * 100.0 / total);
//                    judge.setScore(score);
//                    //根据结果判断最终状态
//                    if (ac == total) {
//                        //全部测试点正确，评测正确ac
//                        judge.setStatus(JudgeStatus.STATUS_ACCEPTED);
//                    } else if (ac == 0) {
//                        //全部测试点错误，评测按照第一个测试点的状态设置
//                        judge.setStatus(judgeCases.get(0).getStatus());
//                    } else {
//                        //否则部分正确，评测部分正确pa
//                        judge.setStatus(JudgeStatus.STATUS_PARTIAL_ACCEPTED);
//                    }
//                } else {
//                    //只有全部测试点正确，评测才算正确
//                    judge.setStatus(JudgeStatus.STATUS_ACCEPTED);
//                    for (JudgeCase judgeCase : judgeCases) {
//                        if (judgeCase.getStatus() != JudgeStatus.STATUS_ACCEPTED) {
//                            judge.setStatus(judgeCase.getStatus());
//                            break;
//                        }
//                    }
//                }
//                //更新评测结果
//                judgeCaseEntityService.saveBatch(judgeCases);
//                judgeEntityService.updateById(judge);
//            }
//            //评测结束删除提交文件
//            rabbitTemplate.convertAndSend(
//                    RabbitMQConstants.FILE_EXCHANGE,
//                    RabbitMQConstants.FILE_CLEAN_ROUTING_KEY,
//                    submissionDir);
//            //如果通过则更新用户ac表
//            //如果为比赛则不记录
//            if (judge.getContestId() > 0 && judge.getStatus() == JudgeStatus.STATUS_ACCEPTED) {
//                rabbitTemplate.convertAndSend(
//                        RabbitMQConstants.JUDGE_EXCHANGE,
//                        RabbitMQConstants.JUDGE_AC_UPDATE_ROUTING_KEY,
//                        message);
//            }
//        }catch (Exception e){
//            log.error("默认评测系统异常",e);
//            judge.setStatus(JudgeStatus.STATUS_SYSTEM_ERROR);
//            judge.setErrorMessage("评测失败，请联系管理员");
//            judgeEntityService.updateById(judge);
//        }
//    }

    private List<TestCase> getAllTestCases(Long problemId){
        String problemCaseDir = BASE_DIR + "/"
                + FileConstants.DIR_PRIVATE + "/"
                + FileConstants.SUB_DIR_PROBLEM + "/"
                + "problem_" + problemId;
        //计算一下样例的个数in,out是成对的
        long caseCount = FileUtils.countFileBySuffix(problemCaseDir, ".in");
        List<TestCase> testCases = new ArrayList<>();
        for(long i=1; i<=caseCount; i++){
            TestCase testCase = new TestCase();
            testCase.setId(i);
            String inputFile = problemCaseDir + "/" + i + ".in";
            String outFile = problemCaseDir + "/" + i + ".out";
            testCase.setInput(FileUtils.readFileString(inputFile));
            testCase.setOutput(FileUtils.readFileString(outFile));
            testCases.add(testCase);
        }
        return testCases;
    }
}
