package com.ymcloud.judge.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.*;
import com.ymcloud.common.exception.contest.*;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.MessageQueueUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.common.utils.ShortSnowflake;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.*;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.judge.service.JudgeService;
import com.ymcloud.pojo.bo.JudgeTask;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.*;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.judge.service.impl
 * @ClassName: JudgeServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  20:15
 * @Description: 评测业务逻辑处理
 */
@Service
public class JudgeServiceImpl extends ServiceImpl<JudgeMapper, Judge> implements JudgeService {

    @Autowired
    private MessageQueueUtils messageQueueUtils;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private LanguageEntityService languageEntityService;

    @Autowired
    private ContestEntityService contestEntityService;

    @Autowired
    private ContestProblemEntityService contestProblemEntityService;

    @Autowired
    private ContestUserEntityService contestUserEntityService;

    @Autowired
    private TestDataService testDataService;

    @Value("${ymcloud.judge.exchange:" + RabbitMQConstants.JUDGE_EXCHANGE + "}")
    private String judgeExchange = RabbitMQConstants.JUDGE_EXCHANGE;

    @Value("${ymcloud.judge.task-routing-key:" + RabbitMQConstants.JUDGE_ROUTING_KEY + "}")
    private String judgeTaskRoutingKey = RabbitMQConstants.JUDGE_ROUTING_KEY;

    /**
     * 提交评测
     * @param judgeDTO 评测请求
     * @return 提交编号ID
     */
    @Override
    public Long submitJudge(JudgeDTO judgeDTO) {
        if(judgeDTO.getIsContest()){
            return contestJudge(judgeDTO);
        }
        //获取当前用户
        User user = SecurityUtils.getUser();
        //查询题目
        String problemId = judgeDTO.getProblemId();
        Problem problem = problemEntityService.lambdaQuery()
                .eq(Problem::getProblemId, problemId)
                .one();
        //评测题目不存在
        if(problem == null){
            throw new ProblemNotExistException();
        }
        //判断题目的权限
        if(problem.getVisibility() == ProblemConstants.CONTEST_PROBLEM){
            throw new JudgeException("请从比赛官网进行提交评测");
        }
        //私有题目暂只支持题目创建者提交
        if(problem.getVisibility() == ProblemConstants.PRIVATE && !ObjectUtil.equals(user.getId(), problem.getAuthorId())){
            throw new ProblemPermissionException();
        }
        //再查询可用语言
        Language language = languageEntityService.lambdaQuery()
                .eq(Language::getName, judgeDTO.getLanguage())
                .eq(Language::getOj, problem.getSource())
                .eq(Language::getStatus, LanguageConstants.ABLE)
                .one();
        if(language == null){
            throw new JudgeException("该题目不支持此编程语言");
        }
        Long testDataVersionId = requireTestDataVersion(problem.getId());
        //创建提交记录实体
        Judge judge = Judge.builder()
                .problemId(problem.getId())
                .testDataVersionId(testDataVersionId)
                .problemTitle(problem.getTitle())
                .displayId(problem.getProblemId())
                .userId(user.getId())
                .username(user.getUsername())
                .language(language.getName())
                .status(JudgeStatus.STATUS_PENDING)
                .code(judgeDTO.getCode())
                .codeLength(judgeDTO.getCode().length())
                .judgeServer(JudgeConstants.LOCAL_JUDGE_SERVER)
                .ip(IpUtils.getIpAddr())
                .build();
        //向数据库中保存评测记录
        if(!judgeEntityService.save(judge)){
            throw new JudgeException("提交失败，请稍后再试");
        }
        //向redis中保存临时轮询查询结果，默认5分钟
        JudgeResultVO judgeResult=new JudgeResultVO();
        judgeResult.setStatus(JudgeStatus.STATUS_PENDING);
        redisCache.setCacheObject(
                CacheConstants.JUDGE_KEY+judge.getId(),
                judgeResult,
                5, TimeUnit.MINUTES);

        //构造评测实体，将实体交给评测队列，等待评测
        JudgeTask task =JudgeTask.builder()
                .submissionId(judge.getId())
                .problemId(problem.getId())
                .testDataVersionId(judge.getTestDataVersionId())
                .code(judgeDTO.getCode())
                .languageConfig(language)
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .mode(problem.getMode())
                .strategy(JudgeConstants.DEFAULT_JUDGE)
                .build();

        //发送消息
        messageQueueUtils.sendWithDelay(
                judgeExchange,
                judgeTaskRoutingKey,
                task,
                5000L);

        //获取当前的评测id，用于前端请求当前评测状态
        return judge.getId();
    }

    /**
     * 比赛提交
     * @param judgeDTO 评测请求体
     * @return 提交id
     */
    private Long contestJudge(JudgeDTO judgeDTO){
        //获取比赛
        Contest contest = contestEntityService.getById(judgeDTO.getContestId());
        if(contest == null){
            throw new ContestNotExistException();
        }
        //判断比赛是否开始
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(contest.getStartTime())){
            throw new ContestNotStartException();
        }
        //检查比赛后是否允许提交
        if (now.isAfter(contest.getEndTime()) && !contest.getAllowEndSubmit()) {
            throw new ContestException("比赛结束后禁止提交");
        }
        //获取当前用户
        User user = SecurityUtils.getUser();
        //如果为私有赛则验证权限
        if(!contest.getVisibility().equals(ContestConstants.VISIBILITY_PUBLIC)){
            boolean access = contestUserEntityService.lambdaQuery()
                    .eq(ContestUser::getContestId, contest.getId())
                    .eq(ContestUser::getUserId, user.getId())
                    .exists();
            if(!access){
                throw new ContestNotAccessException();
            }
        }

        //查询比赛题目
        String displayId = judgeDTO.getProblemId();
        ContestProblem contestProblem = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contest.getId())
                .eq(ContestProblem::getDisplayId, displayId)
                .one();
        if(contestProblem == null){
            throw new ContestProblemNotExistException();
        }
        //再查询题目详细信息
        Problem problem = problemEntityService.getById(contestProblem.getProblemId());
        if(problem == null){
            throw new ProblemNotExistException();
        }
        //查询题目的语言配置
        Language language = languageEntityService.lambdaQuery()
                .eq(Language::getName, judgeDTO.getLanguage())
                .eq(Language::getOj, problem.getSource())
                .eq(Language::getStatus, LanguageConstants.ABLE)
                .one();
        if(language == null){
            throw new JudgeException("该题目不支持此编程语言");
        }

        Long testDataVersionId = requireTestDataVersion(problem.getId());

        //创建提交记录实体
        Judge judge = Judge.builder()
                .contestId(contest.getId())
                .contestProblemId(contestProblem.getId())
                .problemId(problem.getId())
                .testDataVersionId(testDataVersionId)
                .problemTitle(contestProblem.getDisplayTitle())
                .displayId(contestProblem.getDisplayId())
                .userId(user.getId())
                .username(user.getUsername())
                .language(language.getName())
                .status(JudgeStatus.STATUS_PENDING)
                .code(judgeDTO.getCode())
                .codeLength(judgeDTO.getCode().length())
                .judgeServer(JudgeConstants.LOCAL_JUDGE_SERVER)
                .ip(IpUtils.getIpAddr())
                .build();
        //向数据库中保存评测记录
        if(!judgeEntityService.save(judge)){
            throw new JudgeException("提交失败，请稍后再试");
        }
        //向redis中保存临时轮询查询结果，默认5分钟
        JudgeResultVO judgeResult=new JudgeResultVO();
        judgeResult.setStatus(JudgeStatus.STATUS_PENDING);
        redisCache.setCacheObject(
                CacheConstants.JUDGE_KEY+judge.getId(),
                judgeResult,
                5, TimeUnit.MINUTES);

        //构造评测实体，将实体交给评测队列，等待评测
        JudgeTask task =JudgeTask.builder()
                .submissionId(judge.getId())
                .problemId(problem.getId())
                .testDataVersionId(judge.getTestDataVersionId())
                .code(judgeDTO.getCode())
                .languageConfig(language)
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                //以比赛的评测模式为准
                .mode(contest.getMode())
                .strategy(JudgeConstants.DEFAULT_JUDGE)
                .build();

        //比赛立即评测
        messageQueueUtils.send(
                judgeExchange,
                judgeTaskRoutingKey,
                task);

        //获取当前的评测id，用于前端请求当前评测状态
        return judge.getId();
    }

    /**
     * 固定本次提交使用的数据版本，避免排队期间的数据发布改变评测结果。
     */
    private Long requireTestDataVersion(Long problemId) {
        Long versionId = testDataService.getCurrentVersion(problemId);
        if (versionId == null || versionId <= 0) {
            throw new JudgeException("题目暂无可用的测试数据版本");
        }
        return versionId;
    }

    /**
     * 自测提交
     * @param testJudgeDTO 评测请求
     * @return 自测临时请求id
     */
    @Override
    public Long testJudge(TestJudgeDTO testJudgeDTO) {
        //再查询可用语言
        Language language = languageEntityService.lambdaQuery()
                .eq(Language::getName, testJudgeDTO.getLanguage())
                .eq(Language::getOj, "myy")
                .eq(Language::getStatus, LanguageConstants.ABLE)
                .one();
        if(language == null){
            throw new JudgeException("暂不支持此编程语言");
        }

        ShortSnowflake snowflake = new ShortSnowflake(1, 1);
        Long submissionId = snowflake.nextId();

        //向redis中保存临时轮询查询结果，默认5分钟
        JudgeResultVO judgeResult=JudgeResultVO.builder()
                .status(JudgeStatus.STATUS_PENDING)
                .input(testJudgeDTO.getInput())
                .expectedOutput(testJudgeDTO.getExpectedOutput())
                .build();
        redisCache.setCacheObject(
                CacheConstants.JUDGE_KEY+submissionId,
                judgeResult,
                5, TimeUnit.MINUTES);

        //构造评测实体，将实体交给评测队列，等待评测
        JudgeTask task =JudgeTask.builder()
                .submissionId(submissionId)
                .code(testJudgeDTO.getCode())
                .input(testJudgeDTO.getInput())
                .expectedOutput(testJudgeDTO.getExpectedOutput())
                .languageConfig(language)
                .timeLimit(1000L)
                .memoryLimit(256 * 1024L)
                .mode(JudgeConstants.ACM)
                .strategy(JudgeConstants.TEST_JUDGE)
                .build();

        //发送消息
        messageQueueUtils.sendWithDelay(
                judgeExchange,
                judgeTaskRoutingKey,
                task,
                5000L);

        //当前的评测id，用于前端请求当前评测状态
        return submissionId;
    }

    /**
     * 定时评测轮询查询
     * @param submissionId 提交id
     * @return 评测结果
     */
    @Override
    public JudgeResultVO getStatus(Long submissionId) {
        String key = CacheConstants.JUDGE_KEY + submissionId;
        JudgeResultVO judge = redisCache.getCacheObject(key);
        if(judge == null){
            throw new JudgeException("对不起，评测已结束或超时");
        }
        return judge;
    }

}
