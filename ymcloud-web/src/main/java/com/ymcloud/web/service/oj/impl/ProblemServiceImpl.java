package com.ymcloud.web.service.oj.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.oj.ProblemPageQuery;
import com.ymcloud.pojo.query.oj.ProblemSubmissionPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.LanguageVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.pojo.vo.oj.ProblemCountVO;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;
import com.ymcloud.pojo.vo.oj.ProblemVO;
import com.ymcloud.pojo.vo.oj.SubmissionTableVO;
import com.ymcloud.web.service.oj.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:41
 * @Description: 公开题库查询、题目详情与提交记录服务
 */
@Service
public class ProblemServiceImpl implements ProblemService {

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private LanguageEntityService languageEntityService;

    /**
     * 按关键词、难度及所选标签分页查询公开题目并附带提交统计
     * @param query 已校验的查询条件，多标签须同时匹配
     * @return 题目分页结果
     */
    @Override
    public PageVO<ProblemTableVO> pageQuery(ProblemPageQuery query) {
        //使用sql优化查询
        Page<ProblemTableVO> page=new Page<>(query.getPageNo(),query.getPageSize());
        //这里sql限制了只查询权限为公开的题目
        Page<ProblemTableVO> pageResult=problemEntityService.pageQuery(
                page,
                query.getKeyword(),
                query.getDifficulty(),
                query.getTagIds());
        //如果不为空则设置提交情况
        if(pageResult.getTotal()>0){
            //查询对应的题目的ac和总提交数量
            List<ProblemTableVO> records = pageResult.getRecords();
            List<String> problemIds = records.stream().map(ProblemTableVO::getProblemId).toList();
            Map<String, ProblemCountVO> problemCountVOMap = judgeEntityService
                    .getProblemListCount(problemIds).stream()
                    .collect(Collectors.toMap(ProblemCountVO::getProblemId, problemCount -> problemCount));
            //使用map对应设置
            for (ProblemTableVO record : records) {
                ProblemCountVO problemCountVO = problemCountVOMap.get(record.getProblemId());
                if (problemCountVO == null) {
                    continue;
                }
                record.setAcceptCount(problemCountVO.getAc());
                record.setSubmitCount(problemCountVO.getTotal());
            }
            pageResult.setRecords(records);
        }
        //返回分页对象
        return PageVO.of(pageResult,ProblemTableVO.class);
    }

    /**
     * 获取题目信息
     * @param problemId 题目id
     * @return 题目信息
     */
    @Override
    public ProblemVO getProblemInfo(String problemId) {
        //校验参数
        Problem problem = validateProblem(problemId);
        //私有题目查询权限
        Long userId = SecurityUtils.getUserId();
        //管理员权限
        Boolean admin = SecurityUtils.isAdmin();
        if(problem.getVisibility() == ProblemConstants.PRIVATE
                && !admin
                && !Objects.equals(problem.getAuthorId(), userId)){
            throw new ProblemPermissionException("私有题目，请联系题目创建者:"+problem.getAuthorUsername());
        }
        //拷贝前端实体
        ProblemVO problemVO = BeanUtil.copyProperties(problem, ProblemVO.class);
        //查询题目的题面样例,将json转化为对象
        List<ProblemCaseVO> problemCaseVOList = JSON.parseArray(problem.getExamples(), ProblemCaseVO.class);
        problemVO.setProblemCaseList(problemCaseVOList);
        //来源只记录出处，题目统一使用本站启用的语言配置。
        List<LanguageVO> languageVOList = languageEntityService.getByStatusAndOj(
                LanguageConstants.ABLE,
                LanguageConstants.LOCAL_PLATFORM);
        problemVO.setLanguageList(languageVOList);
        //查询用户该题目的通过状态
        boolean isAccepted = judgeEntityService.lambdaQuery()
                .eq(Judge::getProblemId, problem.getId())
                .eq(Judge::getUserId, userId)
                //不能查询比赛提交
                .eq(Judge::getContestId, 0L)
                .eq(Judge::getStatus, JudgeStatus.STATUS_ACCEPTED)
                .exists();
        problemVO.setAccepted(isAccepted);
        //返回题目前端实体
        return problemVO;
    }

    /**
     * 随机获取一道题目
     * @return 题目唯一标识
     */
    @Override
    public String randomProblem() {
        //只随机公共题目
        List<String> list = problemEntityService.lambdaQuery()
                .select(Problem::getProblemId)
                .eq(Problem::getVisibility, ProblemConstants.PUBLIC)
                .list().stream().map(Problem::getProblemId).toList();
        if(CollectionUtil.isEmpty(list)){
            throw new ProblemException("暂时没有公共题目");
        }
        //随机
        int randomInt = RandomUtil.randomInt(list.size());
        return list.get(randomInt);
    }

    /**
     * 查询用户当前题目提交列表
     * @param query 分页查询实体
     * @return 提交列表
     */
    @Override
    public PageVO<SubmissionTableVO> problemSubmissionList(ProblemSubmissionPageQuery query) {
        //检查题目信息
        String problemId = query.getProblemId();
        Problem problem = validateProblem(problemId);
        //获取当前用户信息
        Long userId = SecurityUtils.getUserId();
        //构造分页实体
        Page<Judge> page=new Page<>(query.getPageNo(),query.getPageSize());
        //根据限定条件进行查询
        Page<Judge> judgePage = judgeEntityService.lambdaQuery()
                .eq(Judge::getUserId, userId)
                .eq(Judge::getProblemId, problem.getId())
                //不能查询比赛提交
                .eq(Judge::getContestId, 0L)
                .orderByDesc(Judge::getCreatedTime)
                .page(page);
        //返回提交vo
        return PageVO.of(judgePage,judge->{
            SubmissionTableVO vo= BeanUtil.copyProperties(judge,SubmissionTableVO.class);
            vo.setSubmissionId(judge.getId());
            return vo;
        });
    }

    /**
     * 根据题目唯一标识校验题目信息
     * @param problemId 题目唯一标识
     * @return 当前查询题目
     */
    private Problem validateProblem(String problemId) {
        //校验参数
        if(StrUtil.isBlank(problemId)){
            throw new ProblemException("题目标识参数为空", HttpStatus.BAD_REQUEST);
        }
        Problem problem = problemEntityService.lambdaQuery().eq(Problem::getProblemId, problemId).one();
        if(problem==null){
            //题目不存在或者已删除
            throw new ProblemNotExistException();
        }
        //校验题目是否是比赛题
        if(problem.getVisibility() == ProblemConstants.CONTEST_PROBLEM){
            throw new ProblemPermissionException("请从比赛官网查询题目");
        }
        return problem;
    }

}
