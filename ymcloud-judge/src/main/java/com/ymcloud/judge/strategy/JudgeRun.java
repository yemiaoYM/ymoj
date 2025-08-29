package com.ymcloud.judge.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.ymcloud.common.constant.*;
import com.ymcloud.common.exception.judge.*;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.common.utils.MessageQueueUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.bo.*;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import com.ymcloud.pojo.bo.JudgeTask;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.JudgeResultVO;
import lombok.extern.slf4j.Slf4j;
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
    private MessageQueueUtils messageQueueUtils;

    @Value("${ymcloud.file.base-dir}")
    private String BASE_DIR;
    @Value("${ymcloud.docker.host-working-dir}")
    private String hostWorkingDir;
    @Value("${ymcloud.docker.container-working-dir}")
    private String containerWorkingDir;

    /**
     * 执行判题
     * @param task 判题任务
     */
    public void execute(JudgeTask task){
        log.info("JudgeRun评测逻辑处理...");
        //判断评测策略
        if(task.getStrategy().equals(JudgeConstants.DEFAULT_JUDGE)){
            List<TestCase> testCases=getAllTestCases(task.getProblemId());
            JudgeResult result = judge(task, testCases);
            //更新评测结果
            messageQueueUtils.send(
                    RabbitMQConstants.JUDGE_EXCHANGE,
                    RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY,
                    result);
        } else if (task.getStrategy().equals(JudgeConstants.TEST_JUDGE)){
            List<TestCase> testCases=List.of(new TestCase(1,task.getInput(),task.getExpectedOutput()));
            judge(task, testCases);
        }
    }

    /**
     * 默认模式评测
     */
    private JudgeResult judge(JudgeTask task, List<TestCase> testCases) {
        //评测结果
        JudgeResult result=new JudgeResult();
        result.setSubmissionId(task.getSubmissionId());
        //docker容器
        CodeExecContainer container = null;
        try {
            //通知开始编译
            notifyStatus(result,JudgeStatus.STATUS_COMPILING,null,task.getStrategy());
            //获取语言配置
            Language language = task.getLanguageConfig();
            //本地文件路径
            String hostFilePath = hostWorkingDir + "/" + task.getSubmissionId();
            //从工厂中创建容器
            CodeExecContainerFactory factory = new CodeExecContainerFactory(
                    dockerClient,
                    hostFilePath,
                    containerWorkingDir,
                    task.getMemoryLimit(),
                    language.getEnv());
            container = factory.createDockerContainer();
            //启动容器
            container.start();
            //根据语言选择沙箱判题策略
            SandBoxFactory sandBoxFactory = new SandBoxFactory(language.getName());
            SandBox sandBox = sandBoxFactory.createSandBox(container);
            //保存用户代码
            sandBox.saveCode(task.getCode());
            //编译
            sandBox.compile(container);
            //通知开始评测
            notifyStatus(result,JudgeStatus.STATUS_JUDGING,null,task.getStrategy());
            //评测测试用例
            long maxTimeUsed = 0L;
            long maxMemoryUsed = 0L;
            List<TestCaseResult> testCaseResults=new ArrayList<>();
            for(TestCase testCase:testCases){
                System.out.println("[第"+testCase.getId()+"个]测试样例开始评测...");
                //设置单个用例基本信息
                TestCaseResult caseResult = new TestCaseResult();
                caseResult.setJudgeId(result.getSubmissionId());
                caseResult.setIndex(testCase.getId());

                CodeRunResult codeRunResult = new CodeRunResult();
                try {
                    codeRunResult = sandBox.execute(container, testCase.getInput().trim() + "\n");
                } catch (TimeLimitExceededException e) {
                    caseResult.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
                } catch (MemoryLimitExceededException e){
                    caseResult.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
                } catch (RuntimeErrorException e){
                    caseResult.setStatus(JudgeStatus.STATUS_RUNTIME_ERROR);
                    caseResult.setErrorMessage(e.getMessage());
                }

                if(codeRunResult.getTimeUsed() > task.getTimeLimit()){
                    caseResult.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
                }
                if(codeRunResult.getMemoryUsed() > task.getMemoryLimit() * 1024L) {
                    caseResult.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
                }

                //没有TLE也没有MLE，则比较输出。
                if(caseResult.getStatus() == null){
                    String expectedOutput = StrUtil.trimToEmpty(testCase.getOutput());
                    String actualOutput = StrUtil.trimToEmpty(codeRunResult.getStdout());

                    if(expectedOutput.equals(actualOutput)){
                        caseResult.setStatus(JudgeStatus.STATUS_ACCEPTED);
                    }else {
                        caseResult.setStatus(JudgeStatus.STATUS_WRONG_ANSWER);
                    }

                    maxTimeUsed = Math.max(maxTimeUsed, codeRunResult.getTimeUsed());
                    maxMemoryUsed = Math.max(maxMemoryUsed, codeRunResult.getMemoryUsed());
                    //单个用例设置输出，部分信息
                    int MAX_OUTPUT_LENGTH = 300;
                    if (actualOutput.length() > MAX_OUTPUT_LENGTH) {
                        caseResult.setOutput(actualOutput.substring(0, MAX_OUTPUT_LENGTH) +
                                "\n... (output truncated)");
                    }else{
                        caseResult.setOutput(actualOutput);
                    }
                    //单个用例设置耗时和内存使用
                    caseResult.setTime(codeRunResult.getTimeUsed());
                    caseResult.setMemory(codeRunResult.getMemoryUsed());
                }
                testCaseResults.add(caseResult);
            }
            result.setTestCaseResults(testCaseResults);

            result.setTime(maxTimeUsed);
            result.setMemory(maxMemoryUsed);
            result.setStatus(JudgeStatus.STATUS_ACCEPTED);
            //只有全部测试点正确，评测才算正确
            for (TestCaseResult caseResult : testCaseResults) {
                if (!caseResult.getStatus().equals(JudgeStatus.STATUS_ACCEPTED)) {
                    result.setStatus(caseResult.getStatus());
                    break;
                }
            }
            //通知评测结束
            notifyStatus(result,result.getStatus(),null,task.getStrategy());

            //评测结束删除提交文件
            messageQueueUtils.send(
                    RabbitMQConstants.FILE_EXCHANGE,
                    RabbitMQConstants.FILE_CLEAN_ROUTING_KEY,
                    hostFilePath);
        } catch (CompileException e) {
            notifyStatus(result, JudgeStatus.STATUS_COMPILE_ERROR, e.getMessage(), task.getStrategy());
        } catch (SandBoxException e) {
            log.error("沙箱运行异常", e);
            String errMsg = "对不起，沙箱运行异常，请联系管理员";
            notifyStatus(result, JudgeStatus.STATUS_COMPILE_ERROR, errMsg, task.getStrategy());
        } catch (Exception e) {
            log.error("系统评测异常", e);
            String errMsg = "对不起，系统评测失败，请联系管理员";
            notifyStatus(result, JudgeStatus.STATUS_COMPILE_ERROR, errMsg, task.getStrategy());
        } finally {
            //删除容器
            if (container != null) {
                container.remove(true);
            }
        }
        return result;
    }

    /**
     * 更新评测状态
     * @param result 评测结果
     * @param status 评测状态
     * @param errMsg 错误信息
     * @param strategy   评测策略
     */
    private void notifyStatus(JudgeResult result, JudgeStatus status, String errMsg, Integer strategy) {
        result.setStatus(status);
        result.setErrorMessage(errMsg);

        JudgeResultVO nowStatus = BeanUtil.copyProperties(result, JudgeResultVO.class);
        if(JudgeConstants.TEST_JUDGE.equals(strategy)&&CollUtil.isNotEmpty(result.getTestCaseResults())){
            //如果是自测模式则填充输出信息
            String output = result.getTestCaseResults().get(0).getOutput();
            nowStatus.setOutput(output);
        }

        String key=CacheConstants.JUDGE_KEY + result.getSubmissionId();
        redisCache.setCacheObject(key, nowStatus, 5, TimeUnit.MINUTES);
    }

    private List<TestCase> getAllTestCases(Long problemId){
        String problemCaseDir = BASE_DIR + "/"
                + FileConstants.DIR_PRIVATE + "/"
                + FileConstants.SUB_DIR_PROBLEM + "/"
                + "problem_" + problemId;
        //计算一下样例的个数in,out是成对的
        long caseCount = FileUtils.countFileBySuffix(problemCaseDir, ".in");
        List<TestCase> testCases = new ArrayList<>();
        for(int i=1; i<=caseCount; i++){
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
