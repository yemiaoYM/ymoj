package com.ymcloud.web.controller.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.ProblemDTO;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.ProblemSubmissionPageQuery;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.pojo.vo.ProblemVO;
import com.ymcloud.pojo.vo.SubmissionTableVO;
import com.ymcloud.web.service.ProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * 获取题目信息
     * @param problemDTO 查询题目实体
     * @return 题目信息
     */
    @GetMapping
    public Result<ProblemVO> getProblemInfo(@Validated ProblemDTO problemDTO) {
        log.info("获取题目信息:{}", problemDTO);
        ProblemVO problem=problemService.getProblemInfo(problemDTO);
        return Result.ok(problem);
    }

    /**
     * 用户获取当前题目提交状态
     * @param dto 查询实体
     * @return 状态
     */
    @GetMapping("/submission-status")
    public Result<JudgeStatus> problemSubmissionStatus(@Validated ProblemDTO dto) {
        log.info("用户获取当前题目提交状态:{}", dto);
        JudgeStatus status=problemService.problemSubmissionStatus(dto);
        return Result.ok(status);
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


}
