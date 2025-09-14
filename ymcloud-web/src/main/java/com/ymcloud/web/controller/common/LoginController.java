package com.ymcloud.web.controller.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.ForgetPasswordDTO;
import com.ymcloud.pojo.dto.LoginDTO;
import com.ymcloud.pojo.dto.RegisterDTO;
import com.ymcloud.pojo.dto.ResetPasswordDTO;
import com.ymcloud.web.service.oj.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
     */
    @PostMapping("/register")
    public Result<?> register(@Validated @RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.ok().msg("注册成功");
    }

    /**
     * 获取忘记密码用户的邮箱验证码
     * @param forgetPasswordDTO 请求体
     * @return 验证码的唯一标识
     */
    @PostMapping("/forget-code")
    public Result<String> getForgetCode(@Validated @RequestBody ForgetPasswordDTO forgetPasswordDTO) {
        log.info("用户：{}，电子邮箱为：{}，忘记密码",forgetPasswordDTO.getUsername(),forgetPasswordDTO.getEmail());
        String uuid=userService.getForgetCode(forgetPasswordDTO);
        return Result.ok(uuid).msg("发送成功，有效期为5分钟，请及时验证");
    }

    /**
     * 通过忘记密码来重置密码
     * @param resetPasswordDTO 请求体
     */
    @PutMapping("/forget-password")
    public Result<?> forgetPassword(@Validated @RequestBody ResetPasswordDTO resetPasswordDTO) {
        log.info("用户：{}通过忘记密码来重置密码",resetPasswordDTO.getUsername());
        userService.forgetPassword(resetPasswordDTO);
        return Result.ok().msg("重置密码成功请返回进行登录");
    }

}
