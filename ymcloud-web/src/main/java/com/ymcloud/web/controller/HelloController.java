package com.ymcloud.web.controller;

import com.ymcloud.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    /**
     * 测试开始接口
     * @return
     */
    @GetMapping("/hello")
    public Result<String> hello(){
        return Result.ok("hello ymcloud");
    }

}
