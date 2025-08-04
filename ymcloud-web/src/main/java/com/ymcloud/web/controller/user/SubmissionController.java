package com.ymcloud.web.controller.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.vo.SubmissionVO;
import com.ymcloud.web.service.SubmissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @PackageName: com.ymcloud.web.controller.user
 * @ClassName: SubmissionController
 * @Author: Yemiao
 * @CreateTime: 2025-08-04  12:55
 * @Description: 提交相关控制器
 */
@Slf4j
@RestController
@RequestMapping("/submission")
public class SubmissionController {

    @Autowired
    private SubmissionService submissionService;

    /**
     * 查询提交详情
     * @param submissionId 提交id
     * @return 提交详情信息
     */
    @GetMapping("/detail")
    public Result<SubmissionVO> detail(@RequestParam("submissionId") Long submissionId) {
        log.info("查询提交详情:{}", submissionId);
        SubmissionVO submission=submissionService.getSubmissionDetail(submissionId);
        return Result.ok(submission);
    }



}
