package com.ymcloud.web.controller.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.LoginDTO;
import com.ymcloud.pojo.dto.RegisterDTO;
import com.ymcloud.web.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @PackageName: com.ymcloud.web.controller.user
 * @ClassName: LoginController
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  22:45
 * @Description: 登录注册控制器
 */
@Slf4j
@RestController
public class LoginController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     * @param loginDTO 登录请求体
     * @return token
     */
    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) {
        log.info("用户登录：{}",loginDTO.getUsername());
        String token=userService.login(loginDTO);
        return Result.ok(token).msg("登录成功");
    }

    /**
     * 用户注册
     * @param registerDTO 注册请求体
     * @return 无
     */
    @PostMapping("/register")
    public Result<?> register(@Validated @RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.ok().msg("注册成功");
    }

}
