package com.ymcloud.web.controller;

import com.ymcloud.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HelloController {

    /**
     * 测试开始接口
     */
    @GetMapping("hello")
    public Result<String> hello(){
        return Result.ok("hello ymcloud");
    }

}
