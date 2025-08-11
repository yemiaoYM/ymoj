package com.ymcloud.web.controller;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.websocket.WebSocketService;
import jakarta.websocket.server.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HelloController {

    @Autowired
    private WebSocketService webSocketService;

    /**
     * 测试开始接口
     */
    @GetMapping("hello")
    public Result<String> hello(){
        return Result.ok("hello ymcloud");
    }


    @PostMapping("/public/sendToOne")
    public Result<String> sendToOne(@RequestParam("sid") Long sid, @RequestParam("msg") String msg) {
        log.info("服务器向:{}发送消息:{}", sid, msg);
        webSocketService.sendToOne(sid, msg);
        return Result.ok("发送成功");
    }

    @PostMapping("/public/sendToAll")
    public Result<String> sendToAll(@RequestParam("msg") String msg){
        log.info("服务器向所有在线用户发送消息：{}",msg);
        webSocketService.sendToAll(msg);
        return Result.ok("发送成功");
    }

}
