package com.ymcloud.web.controller.admin.monitor;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.web.domain.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @PackageName: com.ymcloud.web.controller.admin.monitor
 * @ClassName: ServerController
 * @Author: Yemiao
 * @CreateTime: 2025-09-11  23:29
 * @Description: 服务器监控
 */
@RestController
@Slf4j
@RequestMapping("/monitor/server")
public class ServerController {

    /**
     * 获取服务器基本信息
     */
    @GetMapping()
    @PreAuthorize("@ss.hasPermi('monitor:server:list')")
    public Result<Server> getInfo() {
        try {
            Server server = new Server();
            server.copyTo();
            return Result.ok(server).msg("获取服务器信息成功");
        } catch (Exception | LinkageError e) {
            // 原生库初始化失败属于监控采集边界，不能伪造零值或静默丢失诊断。
            log.error("服务器监控采集失败，请检查原生库加载与容器临时目录权限", e);
            return Result.fail("获取服务器信息失败，请联系管理员检查 Web 日志");
        }
    }


}
