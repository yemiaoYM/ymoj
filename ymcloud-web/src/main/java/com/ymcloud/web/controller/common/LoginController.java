package com.ymcloud.web.controller.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.LoginDTO;
import com.ymcloud.web.service.UserService;
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
 * @Description: 登录控制器
 */
@RestController
public class LoginController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) {
        String token=userService.login(loginDTO);
        return Result.ok(token).msg("登录成功");
    }

}
