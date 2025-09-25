package com.ymcloud.web.controller.oj;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.vo.oj.SubmissionVO;
import com.ymcloud.web.service.oj.SubmissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/{id}")
    public Result<SubmissionVO> getSubmissionDetail(@PathVariable(value = "id") Long submissionId) {
        log.info("查询提交详情:{}", submissionId);
        SubmissionVO submission=submissionService.getSubmissionDetail(submissionId);
        return Result.ok(submission);
    }

    /**
     * 修改分享权限
     * @param submissionId 提交id
     */
    @PutMapping("/{id}/share")
    public Result<?> share(@PathVariable("id") Long submissionId) {
        log.info("修改分享权限:{}", submissionId);
        submissionService.updateShare(submissionId);
        return Result.ok();
    }

}
