package com.ymcloud.web.controller.oj;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.query.oj.ContestProblemSubmissionPageQuery;
import com.ymcloud.pojo.query.oj.ContestRankPageQuery;
import com.ymcloud.pojo.query.oj.ContestSubmissionPageQuery;
import com.ymcloud.pojo.vo.*;
import com.ymcloud.web.service.oj.ContestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.user
 * @ClassName: ContestController
 * @Author: Yemiao
 * @CreateTime: 2025-08-13  01:16
 * @Description: 比赛相关业务接口
 */
@RestController
@Slf4j
@RequestMapping("/contest")
public class ContestController {

    @Autowired
    private ContestService contestService;

    /**
     * 获取比赛详情
     * @param contestId 比赛id
     * @return 比赛详情
     */
    @GetMapping("/{id}")
    public Result<ContestVO> getContestDetail(@PathVariable(value = "id") Long contestId) {
        log.info("获取比赛详情:{}", contestId);
        ContestVO contest=contestService.getContestInfo(contestId);
        return Result.ok(contest);
    }

    /**
     * 注册进入比赛
     * @param registerContestDTO 请求体
     */
    @PostMapping("/register")
    public Result<?> registerContest(@Validated @RequestBody RegisterContestDTO registerContestDTO) {
        log.info("注册进入比赛");
        contestService.registerContest(registerContestDTO);
        return Result.ok().msg("进入比赛成功");
    }

    /**
     * 查询比赛题目列表
     * @param contestId 比赛id
     * @return 比赛题目列表
     */
    @GetMapping("/{id}/problems")
    public Result<List<ContestProblemTableVO>> listContestProblems(@PathVariable(value = "id") Long contestId) {
        log.info("查询比赛题目列表：{}", contestId);
        List<ContestProblemTableVO> list=contestService.getContestProblemList(contestId);
        return Result.ok(list);
    }

    /**
     * 查询比赛题目详情
     * @param contestId 比赛id
     * @param displayId 题目展示id
     * @return 比赛题目详情
     */
    @GetMapping("/{contestId}/problem/{displayId}")
    public Result<ProblemVO> getContestProblem(@PathVariable(value = "contestId") Long contestId,
                                               @PathVariable(value = "displayId") String displayId) {
        log.info("查询比赛：{},题目：{}", contestId, displayId);
        ProblemVO problem=contestService.getContestProblem(contestId,displayId);
        return Result.ok(problem);
    }

    /**
     * 查询用户当前比赛题目提交列表
     * @param query 分页查询实体
     * @return 提交列表
     */
    @GetMapping("/problem/submissions")
    public Result<PageVO<SubmissionTableVO>> listProblemSubmissions(@Validated ContestProblemSubmissionPageQuery query) {
        log.info("查询当用当前比赛题目提交列表：{}",query);
        PageVO<SubmissionTableVO> pageVO = contestService.problemSubmissionList(query);
        return Result.ok(pageVO);
    }

    /**
     * 获取比赛评测列表 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @GetMapping("/submissions")
    public Result<PageVO<SubmissionTableVO>> listSubmissions(@Validated ContestSubmissionPageQuery query) {
        log.info("获取比赛评测列表表单:{}",query);
        PageVO<SubmissionTableVO> list=contestService.getSubmissionList(query);
        return Result.ok(list);
    }

    /**
     * 获取比赛排名列表
     * @param query 查询请求题
     * @return 比赛排名列表
     */
    @GetMapping("/ranks")
    public Result<ContestRankTableVO> listContestRanks(@Validated ContestRankPageQuery query) {
        log.info("查询比赛排名列表：{}",query);
        ContestRankTableVO tableVO=contestService.getContestRankList(query);
        return Result.ok(tableVO);
    }


}
