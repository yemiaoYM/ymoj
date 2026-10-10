package com.ymcloud.judge.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.RabbitMQConstants;
import com.ymcloud.common.exception.MqException;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.exception.judge.CompileException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.judge.MemoryLimitExceededException;
import com.ymcloud.common.exception.judge.RuntimeErrorException;
import com.ymcloud.common.exception.judge.SandBoxException;
import com.ymcloud.common.exception.judge.TimeLimitExceededException;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.common.utils.MessageQueueUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.storage.StorageDirectoryResolver;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.judge.bo.CodeRunResult;
import com.ymcloud.judge.bo.JudgeResult;
import com.ymcloud.judge.bo.TestCase;
import com.ymcloud.judge.bo.TestCaseResult;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import com.ymcloud.pojo.bo.JudgeTask;
import com.ymcloud.pojo.bo.TestDataManifest;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    @Autowired
    private TestDataService testDataService;

    @Value("${ymcloud.judge.exchange:" + RabbitMQConstants.JUDGE_EXCHANGE + "}")
    private String judgeExchange = RabbitMQConstants.JUDGE_EXCHANGE;

    @Value("${ymcloud.judge.result-routing-key:" + RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY + "}")
    private String judgeResultRoutingKey = RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY;

    @Value("${ymcloud.docker.host-working-dir}")
    private String hostWorkingDir;
    @Value("${ymcloud.docker.container-working-dir}")
    private String containerWorkingDir;

    /**
     * 执行判题
     * @param task 判题任务
     */
    public void execute(JudgeTask task) {
        log.info("JudgeRun评测逻辑处理...");
        try {
            if (JudgeConstants.DEFAULT_JUDGE.equals(task.getStrategy())) {
                JudgeResult result = judge(task);
                //更新评测结果
                messageQueueUtils.send(
                        judgeExchange,
                        judgeResultRoutingKey,
                        result);
            } else if (JudgeConstants.TEST_JUDGE.equals(task.getStrategy())) {
                judge(task);
            }
        } catch (MqException e) {
            log.warn(e.getMessage(), e);
        } catch (Exception e) {
            log.error("评测逻辑出现异常", e);
        }
    }

    /**
     * 执行正式提交或自测，并记录终态。
     */
    private JudgeResult judge(JudgeTask task) {
        //评测结果
        JudgeResult result = new JudgeResult();
        result.setSubmissionId(task.getSubmissionId());
        //本地文件路径
        String hostFilePath = StorageDirectoryResolver.resolve(hostWorkingDir)
                .resolve(String.valueOf(task.getSubmissionId()))
                .toString();
        //docker容器
        CodeExecContainer container = null;
        try {
            List<TestCaseSource> testCases = getTestCaseSources(task);
            //评测样式严格限制不能为空
            if (CollUtil.isEmpty(testCases)) {
                throw new JudgeException("暂无测试用例，评测失败");
            }
            //通知开始编译
            notifyStatus(result, JudgeStatus.STATUS_COMPILING, null, task.getStrategy());
            //获取语言配置
            Language language = task.getLanguageConfig();
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
            notifyStatus(result, JudgeStatus.STATUS_JUDGING, null, task.getStrategy());
            //评测测试用例
            long maxTimeUsed = 0L;
            long maxMemoryUsed = 0L;
            List<TestCaseResult> testCaseResults = new ArrayList<>();
            for (TestCaseSource source : testCases) {
                //清单只保留引用，当前测试点评测前再读取输入输出。
                TestCase testCase = source.read();
                log.debug("第{}个测试样例开始评测", testCase.getId());
                //设置单个用例基本信息
                TestCaseResult caseResult = new TestCaseResult();
                caseResult.setJudgeId(result.getSubmissionId());
                caseResult.setCaseIndex(testCase.getId());

                CodeRunResult codeRunResult = new CodeRunResult();
                try {
                    codeRunResult = sandBox.execute(container,
                            testCase.getInput(),
                            //增加200ms是消除容器启动等时间
                            task.getTimeLimit() + 200L);
                } catch (TimeLimitExceededException e) {
                    caseResult.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
                } catch (MemoryLimitExceededException e) {
                    caseResult.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
                } catch (RuntimeErrorException e) {
                    caseResult.setStatus(JudgeStatus.STATUS_RUNTIME_ERROR);
                    caseResult.setErrorMessage(e.getMessage());
                }

                if (codeRunResult.getTimeUsed() > task.getTimeLimit()) {
                    caseResult.setStatus(JudgeStatus.STATUS_TIME_LIMIT_EXCEEDED);
                }
                if (codeRunResult.getMemoryUsed() > task.getMemoryLimit() * 1024L) {
                    caseResult.setStatus(JudgeStatus.STATUS_MEMORY_LIMIT_EXCEEDED);
                }

                //没有TLE也没有MLE，则比较输出。
                if (caseResult.getStatus() == null) {
                    String expectedOutput = testCase.getOutput();
                    String actualOutput = codeRunResult.getStdout();

                    if (equalsOutput(expectedOutput, actualOutput)) {
                        caseResult.setStatus(JudgeStatus.STATUS_ACCEPTED);
                    } else {
                        caseResult.setStatus(JudgeStatus.STATUS_WRONG_ANSWER);
                    }

                    maxTimeUsed = Math.max(maxTimeUsed, codeRunResult.getTimeUsed());
                    maxMemoryUsed = Math.max(maxMemoryUsed, codeRunResult.getMemoryUsed());
                    //单个用例设置输出，部分信息
                    int MAX_OUTPUT_LENGTH = 300;
                    if (actualOutput.length() > MAX_OUTPUT_LENGTH) {
                        caseResult.setOutput(actualOutput.substring(0, MAX_OUTPUT_LENGTH) +
                                "\n... (output truncated)");
                    } else {
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

            //根据评测模式设置评测结果
            getStatusByMode(result, task.getMode());

            //通知评测结束
            notifyStatus(result, result.getStatus(), null, task.getStrategy());

        } catch (CompileException e) {
            notifyStatus(result, JudgeStatus.STATUS_COMPILE_ERROR, e.getMessage(), task.getStrategy());
        } catch (SandBoxException e) {
            log.error("沙箱运行异常", e);
            String errMsg = "对不起，沙箱运行异常，请联系管理员";
            notifyStatus(result, JudgeStatus.STATUS_SYSTEM_ERROR, errMsg, task.getStrategy());
        } catch (Exception e) {
            log.error("系统评测异常", e);
            String errMsg = "对不起，系统评测失败，请联系管理员";
            notifyStatus(result, JudgeStatus.STATUS_SYSTEM_ERROR, errMsg, task.getStrategy());
        } finally {
            try {
                //删除容器
                if (container != null) {
                    container.remove(true);
                }
                //评测结束删除提交文件
                if (!FileUtils.deleteFile(hostFilePath)) {
                    throw new FileException("删除提交文件异常");
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * 根据评测模式设置评测状态和分数
     * @param result 当前评测结果
     * @param mode 评测模式
     */
    private void getStatusByMode(JudgeResult result, Integer mode) {
        //获取所有测试用例结果
        List<TestCaseResult> caseResults = result.getTestCaseResults();
        //根据评测模式判断如何计算状态和分数
        if (mode.equals(JudgeConstants.OI)) {
            long total = caseResults.size();
            long ac = caseResults.stream().filter(o -> o.getStatus() == JudgeStatus.STATUS_ACCEPTED).count();

            result.setScore((int) Math.ceil((double) ac / total * 100));

            if (ac == total) {
                result.setStatus(JudgeStatus.STATUS_ACCEPTED);
            } else if (ac == 0) {
                result.setStatus(JudgeStatus.STATUS_WRONG_ANSWER);
            } else {
                result.setStatus(JudgeStatus.STATUS_PARTIAL_ACCEPTED);
            }
        } else if (mode.equals(JudgeConstants.ACM)) {
            result.setStatus(JudgeStatus.STATUS_ACCEPTED);
            result.setScore(100);
            for (TestCaseResult o : caseResults) {
                //查找第一个不为AC的测试用例，更新状态
                if (o.getStatus() != JudgeStatus.STATUS_ACCEPTED) {
                    result.setStatus(o.getStatus());
                    result.setScore(0);
                    break;
                }
            }
        }
    }

    /**
     * 更新评测状态
     * @param result   评测结果
     * @param status   评测状态
     * @param errMsg   错误信息
     * @param strategy 评测策略
     */
    private void notifyStatus(JudgeResult result, JudgeStatus status, String errMsg, Integer strategy) {
        //先更新当前状态
        result.setStatus(status);
        result.setErrorMessage(errMsg);

        String key = CacheConstants.JUDGE_KEY + result.getSubmissionId();
        //先尝试从缓存获取已有对象
        JudgeResultVO nowStatus = redisCache.getCacheObject(key);
        if (nowStatus == null) {
            //缓存不存在则新建
            nowStatus = BeanUtil.copyProperties(result, JudgeResultVO.class);
        } else {
            nowStatus.setStatus(status);
            nowStatus.setErrorMessage(errMsg);
            nowStatus.setTime(result.getTime());
            nowStatus.setMemory(result.getMemory());
        }
        //自测模式下填充输出信息
        if (JudgeConstants.TEST_JUDGE.equals(strategy) && CollUtil.isNotEmpty(result.getTestCaseResults())) {
            TestCaseResult caseResult = result.getTestCaseResults().get(0);
            nowStatus.setOutput(caseResult.getOutput());
            nowStatus.setErrorMessage(caseResult.getErrorMessage());
        }
        //写回缓存，设置过期
        redisCache.setCacheObject(key, nowStatus, 5, TimeUnit.MINUTES);
    }

    /**
     * 获取固定版本的测试点引用；旧队列任务通过只读清单兼容本地数据。
     */
    private List<TestCaseSource> getTestCaseSources(JudgeTask task) {
        if (JudgeConstants.TEST_JUDGE.equals(task.getStrategy())) {
            return List.of(() -> new TestCase(1, task.getInput(), task.getExpectedOutput()));
        }

        Long problemId = task.getProblemId();
        Long versionId = task.getTestDataVersionId();
        TestDataManifest manifest = testDataService.getManifest(problemId, versionId);
        if (manifest == null || !Objects.equals(problemId, manifest.getProblemId())
                || !Objects.equals(versionId, manifest.getVersionId())
                || manifest.getEntries() == null) {
            throw new JudgeException("测试数据清单与提交版本不一致");
        }

        List<TestCaseSource> sources = new ArrayList<>();
        for (TestDataManifest.Entry entry : manifest.getEntries()) {
            sources.add(() -> new TestCase(
                    entry.getId(),
                    readTestData(problemId, versionId, entry.getInput()),
                    readTestData(problemId, versionId, entry.getOutput())));
        }
        return sources;
    }

    /**
     * 流必须完整读取并关闭，使存储服务完成大小和 SHA-256 校验。
     */
    private String readTestData(Long problemId, Long versionId, String logicalPath) throws IOException {
        try (InputStream stream = testDataService.openFile(problemId, versionId, logicalPath)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    private interface TestCaseSource {

        TestCase read() throws IOException;
    }

    /**
     * 标准化 OJ 输出：
     * 1. 统一换行符
     * 2. 去掉每行末尾空格
     * 3. 去掉末尾空行
     */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        //统一成 \n
        s = StrUtil.replace(s, "\r\n", "\n");
        s = StrUtil.replace(s, "\r", "\n");
        //按行切分
        String[] lines = s.split("\n", -1);
        //去掉每行末尾空白
        for (int i = 0; i < lines.length; i++) {
            lines[i] = StrUtil.trimEnd(lines[i]);
        }
        //转成 List 处理末尾空行
        List<String> list = CollUtil.newArrayList(lines);
        //去除末尾空行
        while (!list.isEmpty() && StrUtil.isBlank(list.get(list.size() - 1))) {
            list.remove(list.size() - 1);
        }
        return StrUtil.join("\n", list);
    }

    /**
     * 对比输出（忽略末尾空行与行尾空格）
     */
    private boolean equalsOutput(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }


}
