package com.ymcloud.web.controller.admin.problem;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.admin.problem
 * @ClassName: AdminProblemController
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
     * 查询题目列表
     * @param pageQuery 查询条件
     * @return 题目列表
     */
    @PreAuthorize("hasAuthority('problem:problem:list')")
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
    @PreAuthorize("hasAuthority('problem:problem:query')")
    @GetMapping("/{id}")
    public Result<Problem> getProblemById(@PathVariable("id") Long id) {
        log.info("根据ID查询题目:{}", id);
        Problem problem = adminProblemService.getProblemById(id);
        return Result.ok(problem);
    }

    /**
     * 新增题目
     * @param problemDTO 题目信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:problem:add')")
    @PostMapping
    public Result<Void> addProblem(@RequestBody @Validated ProblemDTO problemDTO) {
        log.info("新增题目:{}", problemDTO);
        adminProblemService.addProblem(problemDTO);
        return Result.ok();
    }

    /**
     * 修改题目
     * @param id 题目ID
     * @param problemDTO 题目信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:problem:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateProblem(@PathVariable("id") Long id, @Validated @RequestBody ProblemDTO problemDTO) {
        log.info("修改题目:{}, 数据:{}", id, problemDTO);
        adminProblemService.updateProblem(id, problemDTO);
        return Result.ok();
    }

    /**
     * 根据ID删除题目
     * @param ids 题目ID列表
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:problem:remove')")
    @DeleteMapping("/{ids}")
    public Result<Void> deleteProblemById(@PathVariable("ids") List<Long> ids) {
        log.info("根据ID删除题目:{}", ids);
        adminProblemService.deleteProblemById(ids);
        return Result.ok();
    }

    /**
     * 修改题目可见性
     * @param id 题目ID
     * @param visibility 可见性：1=公开，2=私有，3=比赛专用
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:problem:edit')")
    @PutMapping("/{id}/visibility")
    public Result<Void> updateProblemVisibility(@PathVariable("id") Long id, @RequestParam("visibility") Integer visibility) {
        log.info("修改题目可见性: id={}, visibility={}", id, visibility);
        adminProblemService.updateProblemVisibility(id, visibility);
        return Result.ok();
    }
}
