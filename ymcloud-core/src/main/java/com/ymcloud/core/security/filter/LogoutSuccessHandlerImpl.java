package com.ymcloud.core.security.filter;

import com.alibaba.fastjson2.JSON;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.result.Result;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.TokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.core.security.filter
 * @ClassName: LogoutSuccessHandlerImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-21  18:25
 * @Description: 用户成功退出处理类
 */
@Component
@Slf4j
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler, Serializable {

    private static final long serialVersionUID = 1L;

    @Autowired
    private TokenService tokenService;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        LoginUser loginUser = tokenService.getLoginUser(request);
        response.setStatus(HttpStatus.SUCCESS);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        if (loginUser != null) {
            String userName = loginUser.getUsername();
            //删除用户缓存记录
            tokenService.delLoginUser(loginUser.getToken());
            log.info("{}用户退出登录成功",userName);
            //返回信息
            String data = JSON.toJSONString(Result.ok().msg("用户退出登录成功"));
            response.getWriter().print(data);
        }else{
            //返回信息
            String data = JSON.toJSONString(Result.fail().msg("用户未登录或token已失效"));
            response.getWriter().print(data);
        }
    }
}
