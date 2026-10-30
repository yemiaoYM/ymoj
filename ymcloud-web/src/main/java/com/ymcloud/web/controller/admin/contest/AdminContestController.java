package com.ymcloud.web.controller.admin.contest;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.admin.ContestProblemTableVO;
import com.ymcloud.web.service.admin.AdminContestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  19:07
 * @Description: 管理端比赛控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/contest")
public class AdminContestController {

    @Autowired
    private AdminContestService adminContestService;

    /**
     * 上传比赛封面，仅允许具备比赛新增或编辑权限的管理员使用。
     * @param file 待上传的公开图片
     * @return 图片公开地址
     */
    @PreAuthorize("@ss.hasAnyPermi('contest:contest:add', 'contest:contest:edit')")
    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("上传比赛封面");
        return Result.ok(adminContestService.uploadImage(file));
    }

    /**
     * 查询比赛列表
     * @param pageQuery 查询条件
     * @return 比赛列表
     */
    @PreAuthorize("@ss.hasPermi('contest:contest:list')")
    @GetMapping("/list")
    public Result<PageVO<Contest>> queryList(@Validated AdminContestPageQuery pageQuery) {
        log.info("查询比赛列表:{}", pageQuery);
        PageVO<Contest> page = adminContestService.queryList(pageQuery);
        return Result.ok(page);
    }

    /**
     * 根据ID查询比赛详情
     * @param id 比赛ID
     * @return 比赛信息
     */
    @PreAuthorize("@ss.hasPermi('contest:contest:query')")
    @GetMapping("/{id}")
    public Result<Contest> getContestById(@PathVariable("id") Long id) {
        log.info("根据ID查询比赛:{}", id);
        Contest contest = adminContestService.getContestById(id);
        return Result.ok(contest);
    }

    /**
     * 新增比赛
     * @param contestDTO 比赛信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:contest:add')")
    @PostMapping
    public Result<Void> addContest(@RequestBody @Validated ContestDTO contestDTO) {
        log.info("新增比赛");
        adminContestService.addContest(contestDTO);
        return Result.ok();
    }

    /**
     * 修改比赛
     * @param id 比赛ID
     * @param contestDTO 比赛信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:contest:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateContest(@PathVariable("id") Long id, @Validated @RequestBody ContestDTO contestDTO) {
        log.info("修改比赛: id={}", id);
        adminContestService.updateContest(id, contestDTO);
        return Result.ok();
    }

    /**
     * 根据ID删除比赛
     * @param ids 比赛ID列表
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:contest:remove')")
    @DeleteMapping("/{ids}")
    public Result<Void> deleteContestById(@PathVariable("ids") List<Long> ids) {
        log.info("根据ID删除比赛:{}", ids);
        adminContestService.deleteContestById(ids);
        return Result.ok();
    }

    /**
     * 修改比赛可见性
     * @param id 比赛ID
     * @param visibility 可见性：0=公开，1=私有
     * @param password 密码（私有比赛时必填）
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:contest:edit')")
    @PutMapping("/{id}/visibility")
    public Result<Void> updateContestVisibility(@PathVariable("id") Long id, 
                                                @RequestParam("visibility") Integer visibility,
                                                @RequestParam(value = "password", required = false) String password) {
        log.info("修改比赛可见性: id={}, visibility={}, hasPassword={}", id, visibility, password != null);
        adminContestService.updateContestVisibility(id, visibility, password);
        return Result.ok();
    }


    /**
     * 克隆比赛
     * @param id 源比赛ID
     * @param title 新比赛标题
     * @param visibility 访问权限：0=公开，1=私有
     * @param password 密码（私有比赛时必填）
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:contest:add')")
    @PostMapping("/{id}/clone")
    public Result<Void> cloneContest(@PathVariable("id") Long id,
                                     @RequestParam("title") String title,
                                     @RequestParam("visibility") Integer visibility,
                                     @RequestParam(value = "password", required = false) String password) {
        log.info("克隆比赛: id={}, title={}, visibility={}, hasPassword={}", id, title, visibility, password != null);
        adminContestService.cloneContest(id, title, visibility, password);
        return Result.ok();
    }

    /**
     * 获取比赛题目列表
     * @param contestId 比赛ID
     * @return 比赛题目列表
     */
    @PreAuthorize("@ss.hasPermi('contest:problem:list')")
    @GetMapping("/{contestId}/problems")
    public Result<List<ContestProblemTableVO>> getContestProblems(@PathVariable("contestId") Long contestId) {
        log.info("获取比赛题目列表: contestId={}", contestId);
        List<ContestProblemTableVO> problems = adminContestService.getContestProblems(contestId);
        return Result.ok(problems);
    }

    /**
     * 获取可添加到比赛的题目列表（包括公开和私有题目）
     * @param contestId 比赛ID
     * @param pageQuery 查询条件
     * @return 可添加的题目列表
     */
    @PreAuthorize("@ss.hasPermi('contest:problem:list')")
    @GetMapping("/{contestId}/available-problems")
    public Result<PageVO<Problem>> getAvailableProblems(@PathVariable("contestId") Long contestId,
                                                        @Validated AdminProblemPageQuery pageQuery) {
        log.info("获取可添加题目列表: contestId={}, query={}", contestId, pageQuery);
        PageVO<Problem> problems = adminContestService.getAvailableProblems(contestId, pageQuery);
        return Result.ok(problems);
    }

    /**
     * 批量添加题目到比赛（会将题目权限设置为比赛专用）
     * @param contestId 比赛ID
     * @param problemIds 题目ID列表
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:problem:add')")
    @PostMapping("/{contestId}/problems/{problemIds}")
    public Result<Void> addProblemsToContest(@PathVariable("contestId") Long contestId,
                                             @PathVariable("problemIds") List<Long> problemIds) {
        log.info("批量添加题目到比赛: contestId={}, problemIds={}", contestId, problemIds);
        adminContestService.addProblemsToContest(contestId, problemIds);
        return Result.ok();
    }

    /**
     * 更新比赛题目的标题和气球颜色，不接受展示 ID 修改
     * @param contestId 比赛ID
     * @param problemId 比赛题目关联 ID
     * @param displayTitle 在比赛中显示的题目标题
     * @param color 气球颜色
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:problem:edit')")
    @PutMapping("/{contestId}/problems/{problemId}")
    public Result<Void> updateContestProblem(@PathVariable("contestId") Long contestId,
                                             @PathVariable("problemId") Long problemId,
                                             @RequestParam("displayTitle") String displayTitle,
                                             @RequestParam("color") String color) {
        log.info("更新比赛题目信息: contestId={}, problemId={}", contestId, problemId);
        adminContestService.updateContestProblem(contestId, problemId, displayTitle, color);
        return Result.ok();
    }

    /**
     * 从比赛中移除题目（会将题目权限设置为私有）
     * @param contestId 比赛ID
     * @param problemIds 题目IDs
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:problem:remove')")
    @DeleteMapping("/{contestId}/problems/{problemIds}")
    public Result<Void> removeContestProblem(@PathVariable("contestId") Long contestId,
                                             @PathVariable("problemIds") List<Long> problemIds) {
        log.info("从比赛中移除题目: contestId={}, problemId={}", contestId, problemIds);
        adminContestService.removeContestProblem(contestId, problemIds);
        return Result.ok();
    }

    /**
     * 保存比赛题目顺序
     * @param contestId 比赛 ID
     * @param ids 完整的有序比赛题目关联 ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('contest:problem:edit')")
    @PutMapping("/{contestId}/problem-order")
    public Result<Void> reorderProblems(@PathVariable("contestId") Long contestId,
                                        @RequestBody List<Long> ids) {
        log.info("调整比赛题目顺序: contestId={}", contestId);
        adminContestService.reorderProblems(contestId, ids);
        return Result.ok();
    }

}
