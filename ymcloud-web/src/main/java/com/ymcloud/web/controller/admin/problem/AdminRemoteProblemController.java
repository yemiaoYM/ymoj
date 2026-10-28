package com.ymcloud.web.controller.admin.problem;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.RemoteProblemDTO;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.web.service.admin.RemoteProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 管理端远程题面复制入口，复用新增题目权限
 */
@Slf4j
@RestController
@RequestMapping("/admin/problem/remote")
public class AdminRemoteProblemController {

    @Autowired
    private RemoteProblemService remoteProblemService;

    /**
     * 单次拉取公开题面用于新增页预填。
     * @param request 平台与题号，不包含登录凭据
     * @return 尚未入库的题面草稿
     */
    @PreAuthorize("@ss.hasPermi('problem:problem:add')")
    @PostMapping
    public Result<RemoteProblemVO> fetch(@Validated @RequestBody RemoteProblemDTO request) {
        log.info("复制远程题面，平台：{}，题号：{}", request.getPlatform(), request.getProblemId());
        return Result.ok(remoteProblemService.fetch(request));
    }
}
