package com.ymcloud.web.controller.admin;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.dto.SubmissionExecutionDTO;
import com.ymcloud.pojo.dto.SubmissionStatusDTO;
import com.ymcloud.web.service.oj.JudgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 普通及比赛提交的超级管理员操作入口
 */
@Slf4j
@RestController
@RequestMapping("/admin/submission")
public class AdminSubmissionController {

    @Autowired
    private JudgeService judgeService;

    /**
     * 按当前配置重新评测单条提交
     * @param id 提交编号
     * @param request 经过结构校验的操作和原执行标识
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:submission:rejudge')")
    @PostMapping("/{id}/rejudge")
    public Result<Void> rejudge(@PathVariable("id") Long id, @Validated @RequestBody SubmissionExecutionDTO request) {
        log.info("重新评测，操作者：{}，提交编号：{}", SecurityUtils.getUserId(), id);
        judgeService.rejudge(id, request.getExecutionId());
        return Result.ok();
    }

    /**
     * 人工设置提交最终状态
     * @param id 提交编号
     * @param request 已校验的状态及部分分数
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:submission:edit')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @Validated @RequestBody SubmissionStatusDTO request) {
        log.info("修改提交状态，操作者：{}，提交编号：{}，目标状态：{}",
                SecurityUtils.getUserId(), id, request.getStatus());
        judgeService.updateSubmissionStatus(id, request);
        return Result.ok();
    }
}
