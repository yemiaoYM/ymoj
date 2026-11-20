package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.pojo.bo.judge.SpjCompileResult;
import com.ymcloud.pojo.bo.judge.SpjProgram;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.dto.admin.SpjCompileDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ProblemVO;
import com.ymcloud.pojo.vo.admin.SpjCompileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.web.judge.SpjCompilation;
import com.ymcloud.web.service.admin.AdminProblemService;
import com.ymcloud.web.service.admin.ResourceDeletionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:45
 * @Description: 管理端题目服务实现类
 */
@Service
public class AdminProblemServiceImpl implements AdminProblemService {

    @Autowired
    private FileEntityService files;

    @Autowired
    private ResourceAccessService resourceAccess;

    @Autowired
    private ResourceDeletionService deletion;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private ProblemTagEntityService problemTagEntityService;

    @Autowired
    private SpjCompilation spjCompilation;

    /**
     * 校验题目管理资格后调用独立 Worker 编译通道
     * @param request 已验证的校验器语言和源码
     * @return 编译结果及仅管理端可见的诊断
     */
    @Override
    public SpjCompileVO compileSpj(SpjCompileDTO request) {
        resourceAccess.requireProblemManager();
        SpjProgram program = new SpjProgram();
        program.setLanguage(request.getLanguage());
        program.setCode(request.getCode());
        program.setDigest(program.sourceDigest());
        SpjCompileResult result = spjCompilation.compile(program);
        SpjCompileVO view = new SpjCompileVO();
        view.setRequestId(result.getRequestId());
        view.setDigest(result.getDigest());
        view.setSuccess(result.isSuccess());
        view.setMessage(result.getMessage());
        return view;
    }

    /**
     * 按管理归属和实时可见性分页查询题目列表
     * @param pageQuery 查询条件
     * @return 题目列表
     */
    @Override
    public PageVO<Problem> queryList(AdminProblemPageQuery pageQuery) {
        resourceAccess.requireProblemManager();
        //创建分页实体
        Page<Problem> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        // 实时状态筛选和总数统计在 SQL 中完成，不逐条查询比赛引用
        Page<Problem> record = problemEntityService.pageManaged(page, pageQuery, resourceAccess.problemScope());

        return PageVO.of(record, Problem.class);
    }

    /**
     * 根据ID查询可管理题目的详情，返回当前有效可见性
     * @param id 题目ID
     * @return 题目信息
     */
    @Override
    public ProblemVO getProblemById(Long id) {
        Problem problem = getManagedProblem(id);
        ProblemVO problemVO = BeanUtil.copyProperties(problem, ProblemVO.class);
        problemVO.setVisibility(resourceAccess.effectiveVisibility(problem));
        //查询题目的题面样例,将json转化为对象
        List<ProblemCaseVO> problemCaseVOList = JSON.parseArray(problem.getExamples(), ProblemCaseVO.class);
        problemVO.setProblemCaseList(problemCaseVOList);
        //查询题目关联的标签ID列表
        List<Long> tagList = problemTagEntityService.lambdaQuery()
                .eq(ProblemTag::getProblemId, problem.getId())
                .list()
                .stream()
                .map(ProblemTag::getTagId)
                .toList();
        problemVO.setTagList(tagList);
        return problemVO;
    }

    /**
     * 使用已校验的自定义展示 ID 添加题目，唯一索引防止并发重复
     * @param problemDTO 通过新增校验的题目信息
     * @return 题目展示 ID
     * @throws ProblemException 展示 ID 已占用、判题方式未支持或保存失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addProblem(ProblemDTO problemDTO) {
        resourceAccess.requireProblemManager();
        validateJudgeMode(problemDTO.getJudgeMode());
        String problemId = problemDTO.getProblemId();
        // 展示 ID 按数据库唯一索引规则判重。
        boolean exists = problemEntityService.lambdaQuery()
                .eq(Problem::getProblemId, problemId)
                .exists();
        if (exists) {
            throw new ProblemException("该题目展示ID已被使用", HttpStatus.CONFLICT);
        }
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
        configureSpj(problem, null);
        problem.setProblemId(problemId);
        problem.setSource(normalizeSource(problemDTO.getSource()));
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
        //将mb转化为kb
        problem.setMemoryLimit(problemDTO.getMemoryLimit()*1024);
        // 新题统一私有，公开状态只能经独立管理入口调整
        problem.setVisibility(ProblemConstants.PRIVATE);
        try {
            if (!problemEntityService.save(problem)) {
                throw new ProblemException("添加题目失败，请稍后再试");
            }
        } catch (DuplicateKeyException cause) {
            ProblemException conflict = new ProblemException("该题目展示ID已被使用", HttpStatus.CONFLICT);
            conflict.initCause(cause);
            throw conflict;
        }
        if (CollUtil.isNotEmpty(problemDTO.getFileIds())) {
            files.bindImages(problemDTO.getFileIds(), FileBizType.PROBLEM, problem.getId(), user.getId());
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
        return problemId;
    }

    /**
     * 修改题目，保留展示 ID 和作者
     * 空样例列表清除旧样例，清空来源或停用特殊判题时同步清除对应字段
     * @param id 题目ID
     * @param problemDTO 题目信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblem(Long id, ProblemDTO problemDTO) {
        validateJudgeMode(problemDTO.getJudgeMode());
        //检查题目是否存在
        Problem existProblem = problemEntityService.lambdaQuery()
                .eq(Problem::getId, id)
                .last("FOR UPDATE")
                .one();
        if (existProblem == null) {
            throw new ProblemNotExistException();
        }
        resourceAccess.requireManageProblem(existProblem);
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
        configureSpj(problem, existProblem);
        problem.setId(id);
        problem.setVisibility(existProblem.getVisibility());
        // 题号与来源无关，编辑出处不改变已有链接和提交记录
        problem.setProblemId(existProblem.getProblemId());
        problem.setSource(normalizeSource(problemDTO.getSource()));
        problem.setMemoryLimit(problemDTO.getMemoryLimit()*1024);
        //保留原有的创建者信息
        problem.setAuthorId(existProblem.getAuthorId());
        problem.setAuthorUsername(existProblem.getAuthorUsername());
        //题目测试用例
        List<ProblemCaseVO> problemCaseList = problemDTO.getProblemCaseList();
        problem.setExamples(JSON.toJSONString(problemCaseList == null ? List.of() : problemCaseList));
        boolean clearSpj = !ProblemConstants.JUDGE_MODE_SPJ.equals(problem.getJudgeMode());
        boolean update = problemEntityService.lambdaUpdate()
                .eq(Problem::getId, id)
                .set(problem.getSource() == null, Problem::getSource, null)
                .set(clearSpj, Problem::getSpjLanguage, null)
                .set(clearSpj, Problem::getSpjCode, null)
                .set(clearSpj, Problem::getSpjDigest, null)
                .update(problem);
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
     * 拒绝尚未实现的交互判题配置
     * @param judgeMode 题目判题方式
     * @throws ProblemException 判题方式不受支持
     */
    private void validateJudgeMode(String judgeMode) {
        if (!ProblemConstants.JUDGE_MODE_DEFAULT.equals(judgeMode)
                && !ProblemConstants.JUDGE_MODE_SPJ.equals(judgeMode)) {
            throw new ProblemException("目前仅支持普通判题和特殊判题", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 保存源码时要求本次编译凭据，未修改的已保存版本可直接复用
     * @param problem 待保存的题目
     * @param existing 已锁定的原题，新增为 null
     */
    private void configureSpj(Problem problem, Problem existing) {
        if (!ProblemConstants.JUDGE_MODE_SPJ.equals(problem.getJudgeMode())) {
            problem.setSpjCode(null);
            problem.setSpjLanguage(null);
            problem.setSpjDigest(null);
            return;
        }
        SpjProgram program = new SpjProgram();
        program.setLanguage(problem.getSpjLanguage());
        program.setCode(problem.getSpjCode());
        String digest = program.sourceDigest();
        if (existing == null || !Objects.equals(digest, existing.getSpjDigest())) {
            spjCompilation.requireCompiled(digest);
        }
        problem.setSpjDigest(digest);
    }

    /**
     * 规范化可选的题目来源说明，空白表示清空
     * @param source 来源文本，可为 null
     * @return 去除首尾空白后的来源，无内容时为 null
     */
    private String normalizeSource(String source) {
        return source == null || source.isBlank() ? null : source.strip();
    }

    /**
     * 根据ID删除题目
     * @param ids 题目ID列表
     */
    @Override
    public void deleteProblemById(List<Long> ids) {
        deletion.deleteProblems(ids);
    }

    /**
     * 修改题目可见性
     * @param id 题目ID
     * @param visibility 可见性：1=公开，2=私有
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblemVisibility(Long id, Integer visibility) {
        if (!SecurityUtils.isAdmin()) {
            throw new ProblemException("仅普通管理员及以上可以修改题目公开状态", HttpStatus.FORBIDDEN);
        }
        //检查可见性参数
        if (visibility == null || (visibility < ProblemConstants.PUBLIC || visibility > ProblemConstants.PRIVATE)) {
            throw new ProblemException("可见性参数错误", HttpStatus.BAD_REQUEST);
        }
        //检查题目是否存在
        Problem problem = problemEntityService.lambdaQuery()
                .eq(Problem::getId, id)
                .last("FOR UPDATE")
                .one();
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        if (problemEntityService.hasActiveContestReference(id, LocalDateTime.now())) {
            throw new ProblemException("题目仍被未结束的非克隆比赛引用，不能修改公开状态");
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

    /**
     * 加载当前用户可以管理的原题
     * @param problemId 题目 ID
     * @return 已通过归属校验的题目
     */
    private Problem getManagedProblem(Long problemId) {
        Problem problem = problemEntityService.getById(problemId);
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        resourceAccess.requireManageProblem(problem);
        return problem;
    }
}
