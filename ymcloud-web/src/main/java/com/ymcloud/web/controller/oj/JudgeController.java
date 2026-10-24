package com.ymcloud.web.controller.oj;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.annotation.RepeatSubmit;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import com.ymcloud.web.service.oj.JudgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-14
 * @Description: 正式提交、自测及状态查询入口
 */
@RestController
@Slf4j
@RequestMapping("/judge")
public class JudgeController {

    @Autowired
    private JudgeService judgeService;

    /**
     * 提交评测
     *
     * @param judgeDTO 评测请求
     * @return 提交编号ID
     */
    @RepeatSubmit
    @PostMapping("/submit")
    public Result<Long> judge(@Validated @RequestBody JudgeDTO judgeDTO) {
        Long submissionId = judgeService.submitJudge(judgeDTO);
        log.info("评测提交成功，提交编号：{}", submissionId);
        return Result.ok(submissionId).msg("评测提交成功");
    }

    /**
     * 自测提交
     *
     * @param testJudgeDTO 评测请求
     * @return 提交编号ID
     */
    @RepeatSubmit
    @PostMapping("/test")
    public Result<Long> test(@Validated @RequestBody TestJudgeDTO testJudgeDTO) {
        Long submissionId = judgeService.testJudge(testJudgeDTO);
        log.info("自测提交成功，提交编号：{}", submissionId);
        return Result.ok(submissionId).msg("自测提交成功");
    }

    /**
     * 定时评测轮询查询
     *
     * @param submissionId 提交id
     * @return 评测结果
     */
    @GetMapping("/status")
    public Result<JudgeResultVO> getStatus(@RequestParam(value = "submissionId") Long submissionId) {
        log.debug("查询评测状态，提交编号：{}", submissionId);
        JudgeResultVO result = judgeService.getStatus(submissionId);
        return Result.ok(result);
    }
}
