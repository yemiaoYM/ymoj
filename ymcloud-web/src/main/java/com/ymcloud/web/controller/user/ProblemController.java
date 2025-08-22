package com.ymcloud.web.controller.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.ProblemAddOrUpdateDTO;
import com.ymcloud.pojo.query.ProblemSubmissionPageQuery;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.pojo.vo.ProblemVO;
import com.ymcloud.pojo.vo.SubmissionTableVO;
import com.ymcloud.web.service.ProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @PackageName: com.ymcloud.web.controller.user
 * @ClassName: ProblemController
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  12:12
 * @Description: 题目相关接口控制器
 */
@RestController
@Slf4j
@RequestMapping("/problem")
public class ProblemController {

    @Autowired
    private ProblemService problemService;

    /**
     * 获取题目信息
     * @param problemId 题目id
     * @return 题目信息
     */
    @GetMapping
    public Result<ProblemVO> getProblemInfo(@RequestParam(value = "problemId") String problemId) {
        log.info("获取题目信息:{}", problemId);
        ProblemVO problem=problemService.getProblemInfo(problemId);
        return Result.ok(problem);
    }

    /**
     * 查询用户当前题目提交列表
     * @param query 分页查询实体
     * @return 提交列表
     */
    @GetMapping("/submission-list")
    public Result<PageVO<SubmissionTableVO>> problemSubmissionList(@Validated ProblemSubmissionPageQuery query) {
        log.info("查询当用当前题目提交列表：{}",query);
        PageVO<SubmissionTableVO> pageVO = problemService.problemSubmissionList(query);
        return Result.ok(pageVO);
    }

    /**
     * 随机获取一道题目
     * @return 题目唯一标识
     */
    @GetMapping("/random")
    public Result<String> randomProblem() {
        log.info("随机获取一道题目");
        String problemId=problemService.randomProblem();
        return Result.ok(problemId);
    }

    /**
     * 添加题目
     * @param problemAddOrUpdateDTO 题目信息
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('problem:add')")
    public Result<?> addProblem(@Validated @RequestBody ProblemAddOrUpdateDTO problemAddOrUpdateDTO) {
        log.info("添加题目：{}",problemAddOrUpdateDTO.getTitle());
        problemService.addProblem(problemAddOrUpdateDTO);
        return Result.ok().msg("添加题目成功");
    }

}
