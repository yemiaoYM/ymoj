package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.web.service.admin.AdminProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: AdminProblemServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:45
 * @Description: 管理端题目服务实现类
 */
@Service
public class AdminProblemServiceImpl implements AdminProblemService {

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private ProblemTagEntityService problemTagEntityService;

    /**
     * 分页查询题目列表
     * @param pageQuery 查询条件
     * @return 题目列表
     */
    @Override
    public PageVO<Problem> queryList(AdminProblemPageQuery pageQuery) {
        //创建分页实体
        Page<Problem> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        //条件分页查询题目列表（管理端可以查看所有可见性的题目）
        Page<Problem> record = problemEntityService.lambdaQuery()
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Problem::getTitle, pageQuery.getKeyword())
                .or(StrUtil.isNotBlank(pageQuery.getKeyword()))
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Problem::getProblemId, pageQuery.getKeyword())
                .eq(pageQuery.getDifficulty() != null, Problem::getDifficulty, pageQuery.getDifficulty())
                .eq(pageQuery.getVisibility() != null, Problem::getVisibility, pageQuery.getVisibility())
                .orderByDesc(Problem::getCreatedTime)
                .page(page);

        return PageVO.of(record, Problem.class);
    }

    /**
     * 根据ID查询题目详情
     * @param id 题目ID
     * @return 题目信息
     */
    @Override
    public Problem getProblemById(Long id) {
        Problem problem = problemEntityService.getById(id);
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        return problem;
    }

    /**
     * 添加题目
     * @param problemDTO 题目信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProblem(ProblemDTO problemDTO) {
        //题目展示id需保证唯一
        String problemId = problemDTO.getProblemId();
        boolean exists = problemEntityService.lambdaQuery().eq(Problem::getProblemId, problemId).exists();
        if (exists) {
            throw new ProblemException("该题目展示id已被使用");
        }
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
        //题目创建者信息
        User user = SecurityUtils.getUser();
        problem.setAuthorId(user.getId());
        problem.setAuthorUsername(user.getUsername());
        //题目测试用例
        List<ProblemCaseVO> problemCaseList = problemDTO.getProblemCaseList();
        if (CollUtil.isNotEmpty(problemCaseList)) {
            String examples = JSON.toJSONString(problemCaseList);
            problem.setExamples(examples);
        }
        //暂不支持远程题目
        problem.setIsRemote(false);
        //如果没有设置可见性，默认为私有
        if (problem.getVisibility() == null) {
            problem.setVisibility(2);
        }
        boolean save = problemEntityService.save(problem);
        if (!save) {
            throw new ProblemException("添加题目失败，请稍后再试");
        }
        //题目标签
        List<Long> tagList = problemDTO.getTagList();
        if (CollUtil.isNotEmpty(tagList)) {
            List<ProblemTag> problemTags = tagList.stream()
                    .map(tagId -> {
                        ProblemTag problemTag = new ProblemTag();
                        problemTag.setProblemId(problem.getId());
                        problemTag.setTagId(tagId);
                        return problemTag;
                    }).toList();
            problemTagEntityService.saveBatch(problemTags);
        }
    }

    /**
     * 修改题目
     * @param id 题目ID
     * @param problemDTO 题目信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblem(Long id, ProblemDTO problemDTO) {
        //检查题目是否存在
        Problem existProblem = problemEntityService.getById(id);
        if (existProblem == null) {
            throw new ProblemNotExistException();
        }
        //检查题目展示id是否重复（排除当前题目）
        String problemId = problemDTO.getProblemId();
        boolean exists = problemEntityService.lambdaQuery()
                .eq(Problem::getProblemId, problemId)
                .ne(Problem::getId, id)
                .exists();
        if (exists) {
            throw new ProblemException("该题目展示id已被使用");
        }
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
        problem.setId(id);
        //保留原有的创建者信息
        problem.setAuthorId(existProblem.getAuthorId());
        problem.setAuthorUsername(existProblem.getAuthorUsername());
        problem.setCreatedTime(existProblem.getCreatedTime());
        //题目测试用例
        List<ProblemCaseVO> problemCaseList = problemDTO.getProblemCaseList();
        if (CollUtil.isNotEmpty(problemCaseList)) {
            String examples = JSON.toJSONString(problemCaseList);
            problem.setExamples(examples);
        }
        //暂不支持远程题目
        problem.setIsRemote(false);
        boolean update = problemEntityService.updateById(problem);
        if (!update) {
            throw new ProblemException("修改题目失败，请稍后再试");
        }
        //更新题目标签
        //先删除原有标签关联
        problemTagEntityService.lambdaUpdate()
                .eq(ProblemTag::getProblemId, id)
                .remove();
        //添加新的标签关联
        List<Long> tagList = problemDTO.getTagList();
        if (CollUtil.isNotEmpty(tagList)) {
            List<ProblemTag> problemTags = tagList.stream()
                    .map(tagId -> {
                        ProblemTag problemTag = new ProblemTag();
                        problemTag.setProblemId(id);
                        problemTag.setTagId(tagId);
                        return problemTag;
                    }).toList();
            problemTagEntityService.saveBatch(problemTags);
        }
    }

    /**
     * 根据ID删除题目
     * @param ids 题目ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblemById(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw new ProblemException("题目id不能为空");
        }
        //检查题目是否存在
        List<Problem> problems = problemEntityService.listByIds(ids);
        if (problems.size() != ids.size()) {
            throw new ProblemException("部分题目不存在或已被删除");
        }
        //删除题目标签关联
        problemTagEntityService.lambdaUpdate()
                .in(ProblemTag::getProblemId, ids)
                .remove();
        //删除题目
        boolean remove = problemEntityService.removeByIds(ids);
        if (!remove) {
            throw new ProblemException("删除题目失败，请稍后再试");
        }
    }

    /**
     * 修改题目可见性
     * @param id 题目ID
     * @param visibility 可见性：1=公开，2=私有，3=比赛专用
     */
    @Override
    public void updateProblemVisibility(Long id, Integer visibility) {
        //检查可见性参数
        if (visibility == null || (visibility < 1 || visibility > 3)) {
            throw new ProblemException("可见性参数错误", HttpStatus.BAD_REQUEST);
        }
        //检查题目是否存在
        Problem problem = problemEntityService.getById(id);
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        //更新可见性
        boolean update = problemEntityService.lambdaUpdate()
                .eq(Problem::getId, id)
                .set(Problem::getVisibility, visibility)
                .update();
        if (!update) {
            throw new ProblemException("修改题目可见性失败，请稍后再试");
        }
    }

}
