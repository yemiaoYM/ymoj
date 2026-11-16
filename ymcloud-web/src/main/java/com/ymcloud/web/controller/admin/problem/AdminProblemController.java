package com.ymcloud.web.controller.admin.problem;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.contract.judge.SpjCompileResult;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.dto.admin.SpjCompileDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ProblemVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
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

    /**
     * 在保存前校验管理员编写的 SPJ，不在 Web 执行代码
     * @param request SPJ 语言和源码
     * @return 编译结果
     */
    @PreAuthorize("@ss.hasAnyPermi('problem:problem:add', 'problem:problem:edit')")
    @PostMapping("/spj/compile")
    public Result<SpjCompileResult> compileSpj(@Validated @RequestBody SpjCompileDTO request) {
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
    public Result<List<ProblemCaseInfo>> getTestCaseFiles(@PathVariable("problemId") Long problemId) {
        log.info("获取题目测试样例文件列表: problemId={}", problemId);
        List<ProblemCaseInfo> testCaseFiles = adminProblemService.getTestCaseFiles(problemId);
        return Result.ok(testCaseFiles);
    }

    /**
     * 上传测试样例文件(支持单个或者多个)
     * @param problemId 题目ID
     * @param file 测试样例文件
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('problem:testcase:add')")
    @PostMapping("/{problemId}/testcases/upload")
    public Result<Void> uploadTestCaseFile(@PathVariable("problemId") Long problemId,
                                           @RequestParam("file") MultipartFile[] file) {
        log.info("上传测试样例文件: problemId={}, fileCount={}", problemId, file.length);
        adminProblemService.uploadTestCaseFile(problemId, file);
        return Result.ok();
    }

    /**
     * 下载测试样例文件
     * @param problemId 题目ID
     * @param fileName 文件名
     * @return 文件资源
     */
    @PreAuthorize("@ss.hasPermi('problem:testcase:query')")
    @GetMapping("/{problemId}/testcases/download")
    public ResponseEntity<Resource> downloadTestCaseFile(@PathVariable("problemId") Long problemId,
                                                         @RequestParam("fileName") String fileName) {
        log.info("下载测试样例文件: problemId={}, fileName={}", problemId, fileName);
        return adminProblemService.downloadTestCaseFile(problemId, fileName);
    }

    /**
     * 删除测试样例文件（支持单个或批量）
     * @param problemId 题目ID
     * @param fileNames 文件名数组
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('problem:testcase:remove')")
    @DeleteMapping("/{problemId}/testcases")
    public Result<Void> deleteTestCaseFiles(@PathVariable("problemId") Long problemId,
                                            @RequestBody List<String> fileNames) {
        log.info("删除测试样例文件: problemId={}, fileNames={}", problemId, fileNames);
        adminProblemService.deleteTestCaseFiles(problemId, fileNames);
        return Result.ok();
    }

}
