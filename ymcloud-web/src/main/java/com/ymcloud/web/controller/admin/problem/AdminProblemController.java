package com.ymcloud.web.controller.admin.problem;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.bo.ImageUploadBO;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.dto.admin.SpjCompileDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ProblemCaseListVO;
import com.ymcloud.pojo.vo.admin.ProblemVO;
import com.ymcloud.pojo.vo.admin.SpjCompileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminProblemService;
import com.ymcloud.web.service.file.ImageService;
import com.ymcloud.web.service.file.ProblemCaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:26
 * @Description: 管理端题目控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/problem")
public class AdminProblemController {

    @Autowired
    private AdminProblemService adminProblemService;

    @Autowired
    private ProblemCaseService problemCases;

    @Autowired
    private ImageService imageService;

    /**
     * 上传题目正文草稿图片
     * @param file 待校验的原始图片
     * @param problemId 已有业务主键，新建时为空
     * @return 十进制文件主键
     */
    @PreAuthorize("@ss.hasAnyPermi('problem:problem:add', 'problem:problem:edit')")
    @PostMapping("/image")
    public Result<Long> uploadProblemImage(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "problemId", required = false) Long problemId) {
        ImageUploadBO bo = ImageUploadBO.builder()
                .bizType(FileBizType.PROBLEM)
                .bizId(problemId)
                .uploaderId(SecurityUtils.getUserId())
                .build();
        Long fileId = imageService.upload(file, bo);
        return Result.ok(fileId);
    }

    /**
     * 在保存前校验管理员编写的 SPJ，不在 Web 执行代码
     * @param request SPJ 语言和源码
     * @return 编译结果
     */
    @PreAuthorize("@ss.hasAnyPermi('problem:problem:add', 'problem:problem:edit')")
    @PostMapping("/spj/compile")
    public Result<SpjCompileVO> compileSpj(@Validated @RequestBody SpjCompileDTO request) {
        log.info("请求编译 SPJ: language={}", request.getLanguage());
        return Result.ok(adminProblemService.compileSpj(request));
    }

    /**
     * 查询题目列表
     * @param pageQuery 查询条件
     * @return 题目列表
     */
    @PreAuthorize("@ss.hasPermi('problem:problem:list')")
    @GetMapping("/list")
    public Result<PageVO<Problem>> queryList(@Validated AdminProblemPageQuery pageQuery) {
        log.info("查询题目列表:{}", pageQuery);
        PageVO<Problem> page = adminProblemService.queryList(pageQuery);
        return Result.ok(page);
    }

    /**
     * 根据ID查询题目详情
     * @param id 题目ID
     * @return 题目信息
     */
    @PreAuthorize("@ss.hasPermi('problem:problem:query')")
    @GetMapping("/{id}")
    public Result<ProblemVO> getProblemById(@PathVariable("id") Long id) {
        log.info("根据ID查询题目:{}", id);
        ProblemVO problem = adminProblemService.getProblemById(id);
        return Result.ok(problem);
    }

    /**
     * 校验自定义展示 ID 及题目信息后新增题目
     * @param problemDTO 题目信息，展示 ID 为1～15位字母、数字或下划线
     * @return 新增题目的展示 ID
     */
    @PreAuthorize("@ss.hasPermi('problem:problem:add')")
    @PostMapping
    public Result<String> addProblem(@RequestBody @Validated(ProblemDTO.Create.class) ProblemDTO problemDTO) {
        log.info("新增题目，展示ID：{}", problemDTO.getProblemId());
        String problemId = adminProblemService.addProblem(problemDTO);
        return Result.ok(problemId);
    }

    /**
     * 修改题目
     * @param id 题目ID
     * @param problemDTO 题目信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('problem:problem:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateProblem(@PathVariable("id") Long id, @Validated @RequestBody ProblemDTO problemDTO) {
        log.info("修改题目，题目ID：{}", id);
        adminProblemService.updateProblem(id, problemDTO);
        return Result.ok();
    }

    /**
     * 根据ID删除题目
     * @param ids 题目ID列表
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('problem:problem:remove')")
    @DeleteMapping("/{ids}")
    public Result<Void> deleteProblemById(@PathVariable("ids") List<Long> ids) {
        log.info("根据ID删除题目:{}", ids);
        adminProblemService.deleteProblemById(ids);
        return Result.ok();
    }

    /**
     * 修改题目可见性
     * @param id 题目ID
     * @param visibility 可见性：1=公开，2=私有
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('problem:problem:publish')")
    @PutMapping("/{id}/visibility")
    public Result<Void> updateProblemVisibility(@PathVariable("id") Long id, @RequestParam("visibility") Integer visibility) {
        log.info("修改题目可见性: id={}, visibility={}", id, visibility);
        adminProblemService.updateProblemVisibility(id, visibility);
        return Result.ok();
    }

    /**
     * 获取题目的所有测试样例文件列表
     * @param problemId 题目ID
     * @return 测试样例文件列表
     */
    @PreAuthorize("@ss.hasPermi('problem:testcase:query')")
    @GetMapping("/{problemId}/testcases")
    public Result<ProblemCaseListVO> getTestCaseFiles(@PathVariable("problemId") Long problemId) {
        return Result.ok(problemCases.list(problemId));
    }

    /**
     * 一次发布选定的成对测试文件
     * @param problemId 题目ID
     * @param version 客户端编辑基线，首次上传为空
     * @param file 成对的输入和输出文件
     * @return 发布成功的清单版本
     */
    @PreAuthorize("@ss.hasPermi('problem:testcase:add')")
    @PostMapping("/{problemId}/testcases/upload")
    public Result<Long> uploadTestCaseFile(@PathVariable("problemId") Long problemId,
                                           @RequestParam(value = "version", required = false) Long version,
                                           @RequestParam("file") MultipartFile[] file) {

        return Result.ok(problemCases.upload(problemId, version, file));
    }

    /**
     * 下载测试样例文件
     * @param problemId 题目ID
     * @param fileId 清单中的文件主键
     * @return 文件资源
     */
    @PreAuthorize("@ss.hasPermi('problem:testcase:query')")
    @GetMapping("/{problemId}/testcases/download")
    public ResponseEntity<Resource> downloadTestCaseFile(@PathVariable("problemId") Long problemId,
                                                         @RequestParam("fileId") Long fileId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(new InputStreamResource(problemCases.openManagedFile(problemId, fileId)));
    }

    /**
     * 删除测试样例文件（支持单个或批量）
     * @param problemId 题目ID
     * @param version 客户端编辑基线
     * @param names 测试点名称列表
     * @return 发布成功的清单版本
     */
    @PreAuthorize("@ss.hasPermi('problem:testcase:remove')")
    @DeleteMapping("/{problemId}/testcases")
    public Result<Long> deleteTestCaseFiles(@PathVariable("problemId") Long problemId,
                                            @RequestParam("version") Long version,
                                            @RequestBody List<String> names) {
        return Result.ok(problemCases.delete(problemId, version, names));
    }

}
