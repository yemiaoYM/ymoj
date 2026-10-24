package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ProblemVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.web.service.admin.AdminProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:45
 * @Description: 管理端题目服务实现类
 */
@Slf4j
@Service
public class AdminProblemServiceImpl implements AdminProblemService {

    @Autowired
    private TestDataService testDataService;

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
                .and(StrUtil.isNotBlank(pageQuery.getKeyword()), query -> query
                        .like(Problem::getTitle, pageQuery.getKeyword())
                        .or()
                        .like(Problem::getProblemId, pageQuery.getKeyword()))
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
    public ProblemVO getProblemById(Long id) {
        Problem problem = problemEntityService.getById(id);
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        ProblemVO problemVO = BeanUtil.copyProperties(problem, ProblemVO.class);
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
     * 添加题目
     * @param problemDTO 题目信息
     * @return 题目展示 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addProblem(ProblemDTO problemDTO) {
        validateJudgeMode(problemDTO.getJudgeMode());
        String problemId = generateLocalProblemId();
        //题目展示id需保证唯一
        boolean exists = problemEntityService.lambdaQuery()
                .eq(Problem::getProblemId, problemId)
                .exists();
        if (exists) {
            throw new ProblemException("该题目展示id已被使用");
        }
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
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
        return problemId;
    }

    /**
     * 修改题目
     * @param id 题目ID
     * @param problemDTO 题目信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblem(Long id, ProblemDTO problemDTO) {
        validateJudgeMode(problemDTO.getJudgeMode());
        //检查题目是否存在
        Problem existProblem = problemEntityService.getById(id);
        if (existProblem == null) {
            throw new ProblemNotExistException();
        }
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
        problem.setId(id);
        // 题号与来源无关，编辑出处不改变已有链接和提交记录。
        problem.setProblemId(existProblem.getProblemId());
        problem.setSource(normalizeSource(problemDTO.getSource()));
        problem.setMemoryLimit(problemDTO.getMemoryLimit()*1024);
        //保留原有的创建者信息
        problem.setAuthorId(existProblem.getAuthorId());
        problem.setAuthorUsername(existProblem.getAuthorUsername());
        //题目测试用例
        List<ProblemCaseVO> problemCaseList = problemDTO.getProblemCaseList();
        if (CollUtil.isNotEmpty(problemCaseList)) {
            String examples = JSON.toJSONString(problemCaseList);
            problem.setExamples(examples);
        }
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
     * 生成下一个本地题目展示id
     * @return 本地题目展示id
     */
    private String generateLocalProblemId() {
        String latestProblemId = problemEntityService.selectLatestLocalProblemIdForUpdate();
        long nextNumber = ProblemConstants.INITIAL_LOCAL_PROBLEM_NUMBER;
        if (StrUtil.isNotBlank(latestProblemId)) {
            nextNumber = Long.parseLong(latestProblemId.substring(ProblemConstants.LOCAL_PROBLEM_ID_PREFIX.length())) + 1;
        }
        return ProblemConstants.LOCAL_PROBLEM_ID_PREFIX + nextNumber;
    }

    /**
     * 拒绝尚未实现的特殊判题和交互判题配置。
     * @param judgeMode 题目判题方式
     * @throws ProblemException 判题方式不受支持
     */
    private void validateJudgeMode(String judgeMode) {
        if (!ProblemConstants.JUDGE_MODE_DEFAULT.equals(judgeMode)) {
            throw new ProblemException("目前仅支持普通判题（default）", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 规范化可选的题目来源说明，空白表示清空。
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

    /**
     * 获取当前版本的测试样例列表，保持原管理接口结构。
     */
    @Override
    public List<ProblemCaseInfo> getTestCaseFiles(Long problemId) {
        return testDataService.listCurrent(problemId);
    }

    /**
     * 以不可变版本发布成对上传的测试数据。
     */
    @Override
    public void uploadTestCaseFile(Long problemId, MultipartFile[] files) {
        testDataService.upload(problemId, files);
    }

    /**
     * 下载时固定一次当前版本，避免并发发布造成文件与清单错配。
     */
    @Override
    public ResponseEntity<Resource> downloadTestCaseFile(Long problemId, String fileName) {
        Problem problem = problemEntityService.lambdaQuery()
                .eq(Problem::getId, problemId)
                .one();
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName == null ? "testcase" : fileName.substring(fileName.lastIndexOf('/') + 1),
                        StandardCharsets.UTF_8)
                .build());
        headers.setContentType(MediaType.TEXT_PLAIN);
        Resource resource = new InputStreamResource(
                testDataService.openFile(problemId, problem.getTestDataVersionId(), fileName));
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * 删除生成新版本，旧对象继续保留给排队任务。
     */
    @Override
    public void deleteTestCaseFiles(Long problemId, List<String> fileNames) {
        testDataService.delete(problemId, fileNames);
    }
}
