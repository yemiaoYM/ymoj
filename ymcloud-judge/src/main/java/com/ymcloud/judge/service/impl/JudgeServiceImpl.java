package com.ymcloud.judge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.*;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.common.utils.ShortSnowflake;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.judge.domain.JudgeMessage;
import com.ymcloud.judge.domain.TestJudge;
import com.ymcloud.judge.service.JudgeService;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.JudgeResultVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private LanguageEntityService languageEntityService;

    /**
     * 提交评测
     * @param judgeDTO 评测请求
     * @return 提交编号ID
     */
    @Override
    public Long submitJudge(JudgeDTO judgeDTO) {
        //获取当前用户
        User user = SecurityUtils.getUser();
        //防止重复提交和提交太快
        String key= CacheConstants.REPEAT_SUBMIT_KEY + user.getUsername();
        if (redisCache.hasKey(key)) {
            throw new ServiceException("提交太快，稍等一下再提交");
        }
        //判断是否为比赛提交
        if(judgeDTO.getIsContest()){
            throw new ServiceException("暂不支持比赛提交");
        }
        //查询题目
        String problemId = judgeDTO.getProblemId();
        Problem problem = problemEntityService.lambdaQuery().eq(Problem::getProblemId, problemId).one();
        //评测题目不存在
        if(problem == null){
            throw new ProblemNotExistException();
        }
        //判断题目的权限
        if(problem.getVisibility() == ProblemConstants.CONTEST_PROBLEM){
            throw new ServiceException("暂不支持比赛提交");
        }
        //私有题目暂只支持题目创建者提交
        if(problem.getVisibility() == ProblemConstants.PRIVATE && !ObjectUtil.equals(user.getId(), problem.getAuthorId())){
            throw new ProblemPermissionException();
        }
        //再查询可用语言
        Language language = languageEntityService.lambdaQuery().eq(Language::getName, judgeDTO.getLanguage())
                .eq(Language::getOj, problem.getSource())
                .eq(Language::getStatus, LanguageConstants.ABLE)
                .one();
        if(language == null){
            throw new JudgeException("该题目不支持此编程语言");
        }
        //创建提交记录实体
        Judge submission = new Judge();
        submission.setProblemId(problem.getId());
        submission.setProblemTitle(problem.getTitle());
        submission.setDisplayId(problem.getProblemId());
        submission.setUserId(user.getId());
        submission.setUsername(user.getUsername());
        submission.setLanguage(language.getName());
        //评测状态为评测排队中
        submission.setStatus(JudgeStatus.STATUS_PENDING);
        submission.setCode(judgeDTO.getCode());
        submission.setCodeLength(judgeDTO.getCode().length());
        submission.setJudgeServer(JudgeConstants.LOCAL_JUDGE_SERVER);
        //这里重新再获取提交id，防止用户异地登录多设备提交，后续添加控制提交ip功能
        submission.setIp(IpUtils.getIpAddr());
        //向数据库中保存评测记录
        boolean save = judgeEntityService.save(submission);
        if(!save){
            throw new JudgeException("提交失败，请稍后再试");
        }
        //构造评测实体，将实体交给评测队列，等待评测
        JudgeMessage message = BeanUtil.copyProperties(submission, JudgeMessage.class);
        message.setSubmissionId(submission.getId());
        message.setLanguageConfig(language);
        message.setTimeLimit(problem.getTimeLimit());
        message.setMemoryLimit(problem.getMemoryLimit());
        message.setMode(problem.getMode());
        message.setStrategy(JudgeConstants.DEFAULT_JUDGE);
        //发送消息
        rabbitTemplate.convertAndSend(RabbitMQConstants.JUDGE_EXCHANGE,
                RabbitMQConstants.JUDGE_ROUTING_KEY, message,message1 -> {
                    message1.getMessageProperties().setDelayLong(5000L);
                    return message1;
                });
        //缓存提交
        redisCache.setCacheObject(key, submission.getId(), 10, TimeUnit.SECONDS);
        //获取当前的评测id，用于前端请求当前评测状态
        return submission.getId();
    }

    /**
     * 自测提交
     * @param testJudgeDTO 评测请求
     * @return 自测临时请求id
     */
    @Override
    public Long testJudge(TestJudgeDTO testJudgeDTO) {
        //获取当前用户
        User user = SecurityUtils.getUser();
        //防止重复提交和提交太快
        String key= CacheConstants.REPEAT_SUBMIT_KEY + user.getUsername();
        if (redisCache.hasKey(key)) {
            throw new ServiceException("提交太快，稍等一下再提交");
        }
        //再查询可用语言
        Language language = languageEntityService.lambdaQuery()
                .eq(Language::getName, testJudgeDTO.getLanguage())
                .eq(Language::getOj, "myy")
                .eq(Language::getStatus, LanguageConstants.ABLE)
                .one();
        if(language == null){
            throw new JudgeException("自测只支持本地语言");
        }
        //将评测实体存储在redis中,默认存储3分钟
        ShortSnowflake snowflake = new ShortSnowflake(1, 1);
        Long submissionId = snowflake.nextId();
        TestJudge judge = new TestJudge();
        judge.setSubmissionId(submissionId);
        judge.setUserId(user.getId());
        judge.setLanguage(language.getName());
        judge.setCode(testJudgeDTO.getCode());
        judge.setInput(testJudgeDTO.getInput());
        judge.setStatus(JudgeStatus.STATUS_PENDING);
        redisCache.setCacheObject(CacheConstants.JUDGE_KEY + submissionId, judge, 3, TimeUnit.MINUTES);
        //自测构造评测实体
        JudgeMessage message = BeanUtil.copyProperties(judge, JudgeMessage.class);
        message.setLanguageConfig(language);
        message.setStrategy(JudgeConstants.TEST_JUDGE);
        //将评测信息交给评测队列
        rabbitTemplate.convertAndSend(RabbitMQConstants.JUDGE_EXCHANGE,
                RabbitMQConstants.JUDGE_ROUTING_KEY, message,message1 -> {
                    message1.getMessageProperties().setDelayLong(5000L);
                    return message1;
                });
        //缓存提交
        redisCache.setCacheObject(key, submissionId, 10, TimeUnit.SECONDS);
        return submissionId;
    }

    /**
     * 定时评测轮询查询
     * @param submissionId 提交id
     * @param isTest 是否为自测提交
     * @return 评测结果
     */
    @Override
    public JudgeResultVO getStatus(Long submissionId,Boolean isTest) {
        if(isTest){
            //自测提交
            String key = CacheConstants.JUDGE_KEY + submissionId;
            TestJudge judge = redisCache.getCacheObject(key);
            if(judge == null){
                throw new JudgeException("自测提交超时或不存在，请重新提交");
            }
            return BeanUtil.copyProperties(judge, JudgeResultVO.class);
        }
        //正式提交
        Judge judge = judgeEntityService.getById(submissionId);
        if(judge == null){
            throw new JudgeException("提交不存在");
        }
        return BeanUtil.copyProperties(judge, JudgeResultVO.class);
    }

}
