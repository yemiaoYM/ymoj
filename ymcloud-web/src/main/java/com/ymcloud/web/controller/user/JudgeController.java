package com.ymcloud.web.controller.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.judge.service.JudgeService;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @PackageName: com.ymcloud.web.controller.user
 * @ClassName: JudgeController
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  15:23
 * @Description: 评测控制器 (评测从这里正式开始)
 */
@RestController
@Slf4j
@RequestMapping("/judge")
public class JudgeController {

    @Autowired
    private JudgeService judgeService;

    /**
     * 提交评测
     * @param judgeDTO 评测请求
     * @return 提交编号ID
     */
    @PostMapping("/submit")
    public Result<Long> judge(@Validated @RequestBody JudgeDTO judgeDTO) {
        log.info("评测请求：{}", judgeDTO);
        Long submissionId=judgeService.submitJudge(judgeDTO);
        return Result.ok(submissionId).msg("评测提交成功");
    }

    /**
     * 自测提交
     * @param testJudgeDTO 评测请求
     * @return 提交编号ID
     */
    @PostMapping("/test")
    public Result<Long> test(@Validated @RequestBody TestJudgeDTO testJudgeDTO) {
        log.info("自测请求：{}", testJudgeDTO);
        Long submissionId=judgeService.testJudge(testJudgeDTO);
        return Result.ok(submissionId).msg("自测提交成功");
    }

}
