package com.ymcloud.web.service.impl;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.mapper.LanguageMapper;
import com.ymcloud.core.web.mapper.ProblemCaseMapper;
import com.ymcloud.core.web.mapper.ProblemMapper;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.ProblemPageQuery;
import com.ymcloud.pojo.vo.*;
import com.ymcloud.web.service.ProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

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
    private ProblemCaseMapper problemCaseMapper;

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
                query.getTitle(),
                query.getDifficulty(),
                query.getSource(),
                query.getTagIds(),
                query.getTagIds().size());
        //如果不为空则设置提交情况
        if(pageResult.getTotal()>0){
            //查询对应的题目的ac和总提交数量
            List<ProblemTableVO> records = pageResult.getRecords();
            List<String> problemIds = records.stream().map(ProblemTableVO::getDisplayId).toList();

        }
        //返回分页对象
        return PageVO.of(pageResult,ProblemTableVO.class);
    }

    /**
     * 根据题目id获取题目信息
     * @param problemId 题目id
     * @return 题目
     */
    @Override
    public ProblemVO getProblemById(String problemId) {
        //校验参数
        if(problemId==null){
            throw new IllegalArgumentException("参数异常");
        }
        //根据题目id查询
        Problem problem = lambdaQuery().eq(Problem::getProblemId, problemId).one();
        if(problem==null){
            //题目不存在或者已删除
            throw new ProblemNotExistException();
        }
        if(problem.getVisibility() == ProblemConstants.CONTEST_PROBLEM){
            throw new ProblemPermissionException("请从比赛官网查询题目");
        }
        if(problem.getVisibility() == ProblemConstants.PRIVATE){
            //私有题目查询权限
            Long userId = SecurityUtils.getUserId();
            if(!Objects.equals(problem.getAuthorId(), userId)){
                throw new ProblemPermissionException("没有访问此题目的权限，请联系题目创建者");
            }
        }
        //拷贝前端实体
        ProblemVO problemVO = BeanUtil.copyProperties(problem, ProblemVO.class);
        //查询题目的题面样例
        //直接使用sql语言来优化性能
        List<ProblemCaseVO> problemCaseVOList =problemCaseMapper.getByProblemIdAndIsSample(problem.getId(),ProblemConstants.CASE_SHOW);
        problemVO.setProblemCaseList(problemCaseVOList);
        //查询题目的语言配置
        //题目的语言选项可用语言的oj来源决定
        //直接使用sql语言来优化性能
        List<LanguageVO> languageVOList = languageMapper.getByStatusAndOj(LanguageConstants.ABLE, problem.getSource());
        problemVO.setLanguageList(languageVOList);
        //返回题目前端实体
        return problemVO;
    }


}
