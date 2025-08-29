package com.ymcloud.judge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.*;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.IpUtils;
import com.ymcloud.common.utils.MessageQueueUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.*;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.judge.service.JudgeService;
import com.ymcloud.pojo.bo.JudgeTask;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.*;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.JudgeResultVO;
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

    /**
     * 提交评测
     * @param judgeDTO 评测请求
     * @return 提交编号ID
     */
    @Override
    public Long submitJudge(JudgeDTO judgeDTO) {
        //获取当前用户
        User user = SecurityUtils.getUser();
        //查询题目
        String problemId = judgeDTO.getProblemId();
        Problem problem = problemEntityService.lambdaQuery().eq(Problem::getProblemId, problemId).one();
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
        //创建提交记录实体
        Judge judge = Judge.builder()
                .problemId(problem.getId())
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
                .code(judgeDTO.getCode())
                .languageConfig(language)
                .timeLimit(problem.getTimeLimit())
                .memoryLimit(problem.getMemoryLimit())
                .mode(problem.getMode())
                .strategy(JudgeConstants.DEFAULT_JUDGE)
                .build();

        //发送消息
        messageQueueUtils.sendWithDelay(
                RabbitMQConstants.JUDGE_EXCHANGE,
                RabbitMQConstants.JUDGE_ROUTING_KEY,
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
       return null;
    }

    /**
     * 自测提交
     * @param testJudgeDTO 评测请求
     * @return 自测临时请求id
     */
    @Override
    public Long testJudge(TestJudgeDTO testJudgeDTO) {
        return null;
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
