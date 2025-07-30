package com.ymcloud.judge.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.IpUtil;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.judge.service.JudgeService;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.JudgeStatus;
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
        if(judgeDTO.getContestSubmission()){
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
        submission.setIp(IpUtil.getIpAddr());
        //向数据库中保存评测记录
        boolean save = judgeEntityService.save(submission);
        if(!save){
            throw new JudgeException("提交失败，请稍后再试");
        }
        //构造评测实体，将实体交给评测队列，等待评测



        //缓存提交
        redisCache.setCacheObject(key, submission.getId(), 10, TimeUnit.SECONDS);
        //获取当前的评测id，用于前端请求当前评测状态
        return submission.getId();
    }
}
