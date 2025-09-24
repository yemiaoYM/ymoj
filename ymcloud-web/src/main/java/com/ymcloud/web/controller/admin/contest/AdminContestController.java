package com.ymcloud.web.controller.admin.contest;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.web.service.admin.AdminContestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.admin.contest
 * @ClassName: AdminContestController
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
     * 查询比赛列表
     * @param pageQuery 查询条件
     * @return 比赛列表
     */
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
    @PostMapping
    public Result<Void> addContest(@RequestBody @Validated ContestDTO contestDTO) {
        log.info("新增比赛:{}", contestDTO);
        adminContestService.addContest(contestDTO);
        return Result.ok();
    }

    /**
     * 修改比赛
     * @param id 比赛ID
     * @param contestDTO 比赛信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public Result<Void> updateContest(@PathVariable("id") Long id, @Validated @RequestBody ContestDTO contestDTO) {
        log.info("修改比赛:{}, 数据:{}", id, contestDTO);
        adminContestService.updateContest(id, contestDTO);
        return Result.ok();
    }

    /**
     * 根据ID删除比赛
     * @param ids 比赛ID列表
     * @return 操作结果
     */
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
    @PostMapping("/{id}/clone")
    public Result<Void> cloneContest(@PathVariable("id") Long id,
                                     @RequestParam("title") String title,
                                     @RequestParam("visibility") Integer visibility,
                                     @RequestParam(value = "password", required = false) String password) {
        log.info("克隆比赛: id={}, title={}, visibility={}, hasPassword={}", id, title, visibility, password != null);
        adminContestService.cloneContest(id, title, visibility, password);
        return Result.ok();
    }
}
