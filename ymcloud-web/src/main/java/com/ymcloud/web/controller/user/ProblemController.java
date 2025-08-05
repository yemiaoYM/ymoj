package com.ymcloud.web.controller.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.ProblemVO;
import com.ymcloud.web.service.ProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * 根据题目id获取题目信息
     * @param problemId 题目唯一标识
     * @return 题目
     */
    @GetMapping
    public Result<ProblemVO> getProblemInfo(@RequestParam("problemId") String problemId) {
        log.info("根据题目id获取题目信息:{}", problemId);
        ProblemVO problem=problemService.getProblemById(problemId);
        return Result.ok(problem);
    }

    /**
     * 用户获取当前题目提交状态
     * @param problemId 题目唯一标识
     * @return 状态
     */
    @GetMapping("/submission-status")
    public Result<JudgeStatus> problemSubmissionStatus(@RequestParam("problemId") String problemId) {
        log.info("用户获取当前题目提交状态:{}", problemId);
        JudgeStatus status=problemService.problemSubmissionStatus(problemId);
        return Result.ok(status);
    }
}
