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
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.bo.TestDataMigrationReport;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ProblemVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.web.service.admin.AdminProblemService;
import com.ymcloud.web.service.admin.ResourceDeletionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    private ResourceAccessService resourceAccess;

    @Autowired
    private ResourceDeletionService deletion;

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private ProblemTagEntityService problemTagEntityService;

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
     * 使用已校验的自定义展示 ID 添加题目，唯一索引防止并发重复。
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
     * 空样例列表会清除旧样例
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
     * 获取当前版本的测试样例列表，保持原管理接口结构
     * @param problemId 题目 ID
     * @return 当前版本的文件清单
     */
    @Override
    public List<ProblemCaseInfo> getTestCaseFiles(Long problemId) {
        getManagedProblem(problemId);
        return testDataService.listCurrent(problemId);
    }

    /**
     * 以不可变版本发布成对上传的测试数据
     * @param problemId 题目 ID
     * @param files 输入输出文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadTestCaseFile(Long problemId, MultipartFile[] files) {
        getManagedProblem(problemId);
        testDataService.upload(problemId, files);
    }

    /**
     * 下载时固定一次当前版本，避免并发发布造成文件与清单错配
     * @param problemId 题目 ID
     * @param fileName 版本内的文件名
     * @return 当前版本文件流
     */
    @Override
    public ResponseEntity<Resource> downloadTestCaseFile(Long problemId, String fileName) {
        Problem problem = getManagedProblem(problemId);
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
     * 删除生成新版本，旧对象继续保留给排队任务
     * @param problemId 题目 ID
     * @param fileNames 待移除文件名
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestCaseFiles(Long problemId, List<String> fileNames) {
        getManagedProblem(problemId);
        testDataService.delete(problemId, fileNames);
    }

    /**
     * 按题目管理归属检查历史数据迁移入口
     * @param problemId 题目 ID
     * @param execute 是否执行迁移，false 仅盘点
     * @return 迁移或盘点报告
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestDataMigrationReport migrateTestData(Long problemId, boolean execute) {
        getManagedProblem(problemId);
        return testDataService.migrateLegacy(problemId, execute);
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
