package com.ymcloud.web.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.LanguageMapper;
import com.ymcloud.core.web.mapper.ProblemMapper;
import com.ymcloud.pojo.dto.ProblemDTO;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.ProblemPageQuery;
import com.ymcloud.pojo.query.ProblemSubmissionPageQuery;
import com.ymcloud.pojo.vo.*;
import com.ymcloud.web.service.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: ProblemServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:41
 * @Description: ProblemServiceImpl
 */
@Service
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private LanguageMapper languageMapper;

    /**
     * 获取题目列表表单 分页查询
     * @param query 查询实体
     * @return 题目列表
     */
    @Override
    public PageVO<ProblemTableVO> pageQuery(ProblemPageQuery query) {
        //使用sql优化查询
        Page<ProblemTableVO> page=new Page<>(query.getPageNo(),query.getPageSize());
        Page<ProblemTableVO> pageResult=getBaseMapper().pageQuery(
                page,
                query.getKeyword(),
                query.getDifficulty(),
                query.getSource());
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
                if(problemCountVO == null)continue;
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
        if(problem.getVisibility() == ProblemConstants.PRIVATE){
            Long userId = SecurityUtils.getUserId();
            if(!Objects.equals(problem.getAuthorId(), userId)){
                throw new ProblemPermissionException("私有题目，请联系题目创建者:"+problem.getAuthorUsername());
            }
        }
        //拷贝前端实体
        ProblemVO problemVO = BeanUtil.copyProperties(problem, ProblemVO.class);
        //查询题目的题面样例,将json转化为对象
        List<ProblemCaseVO> problemCaseVOList = JSON.parseArray(problem.getExamples(), ProblemCaseVO.class);
        problemVO.setProblemCaseList(problemCaseVOList);
        //查询题目的语言配置
        //题目的语言选项可用语言的oj来源决定
        //直接使用sql语言来优化性能
        List<LanguageVO> languageVOList = languageMapper.getByStatusAndOj(LanguageConstants.ABLE, problem.getSource());
        problemVO.setLanguageList(languageVOList);
        //返回题目前端实体
        return problemVO;
    }

    /**
     * 用户获取当前题目提交状态
     * @param problemId 题目id
     * @return 状态
     */
    @Override
    public JudgeStatus problemSubmissionStatus(String problemId) {
        //校验参数
        Problem problem=validateProblem(problemId);
        //私有题目查询权限
        Long userId = SecurityUtils.getUserId();
        if(problem.getVisibility() == ProblemConstants.PRIVATE) {
            if(!Objects.equals(problem.getAuthorId(), userId)){
                throw new ProblemPermissionException("没有访问此题目的权限，请联系题目创建者");
            }
        }
        //查询该用户该题目的提交列表
        List<Judge> submissionList = judgeEntityService.lambdaQuery().eq(Judge::getProblemId, problem.getId())
                .eq(Judge::getUserId, userId)
                .orderByDesc(Judge::getCreatedTime)
                .list();
        //没有提交记录
        if(CollectionUtil.isEmpty(submissionList)){
            return JudgeStatus.STATUS_NULL;
        }
        //提交结果是倒序，相当于设置最近一次的提交状态
        JudgeStatus status=submissionList.get(0).getStatus();
        //如果存在通过，直接返回通过
        for(Judge judge : submissionList){
            if(judge.getStatus() == JudgeStatus.STATUS_ACCEPTED){
                status = judge.getStatus();
                break;
            }
        }
        return status;
    }

    /**
     * 随机获取一道题目
     * @return 题目唯一标识
     */
    @Override
    public String randomProblem() {
        //只随机公共题目
        List<String> list = lambdaQuery().select(Problem::getProblemId)
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
        //检查是否为比赛提交
        if(query.getIsContest()){
            throw new ProblemException("暂不支持查询比赛提交");
        }
        //检查题目信息
        Problem problem = validateProblem(query.getProblemId());
        //获取当前用户信息
        Long userId = SecurityUtils.getUserId();
        //构造分页实体
        Page<Judge> page=new Page<>(query.getPageNo(),query.getPageSize());
        //根据限定条件进行查询
        Page<Judge> judgePage = judgeEntityService.lambdaQuery()
                .eq(Judge::getUserId, userId)
                .eq(Judge::getProblemId, problem.getId())
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
        //1.校验参数
        if(problemId==null || StrUtil.isBlank(problemId)){
            throw new ProblemException("题目标识参数为空", HttpStatus.BAD_REQUEST);
        }
        //2.根据题目id查询
        Problem problem = lambdaQuery().eq(Problem::getProblemId, problemId).one();
        if(problem==null){
            //题目不存在或者已删除
            throw new ProblemNotExistException();
        }
        if(problem.getVisibility() == ProblemConstants.CONTEST_PROBLEM){
            throw new ProblemPermissionException("请从比赛官网查询题目");
        }
        return problem;
    }

}
