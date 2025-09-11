package com.ymcloud.web.controller.admin.monitor;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.web.domain.Server;
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
@RequestMapping("/monitor/server")
public class ServerController {

    /**
     * 获取服务器基本信息
     */
    @GetMapping()
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public Result<Server> getInfo(){
        try {
            Server server = new Server();
            server.copyTo();
            return Result.ok(server).msg("获取服务器信息成功");
        }catch (Exception e){
            return Result.fail("获取服务器信息失败");
        }
    }


}
