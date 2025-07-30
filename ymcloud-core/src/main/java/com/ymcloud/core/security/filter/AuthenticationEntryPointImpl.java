package com.ymcloud.core.security.filter;

import com.alibaba.fastjson2.JSON;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.core.security.filter
 * @ClassName: AuthenticationEntryPointImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-21  18:11
 * @Description: 认证失败处理类 返回未授权
 */
@Component
@Slf4j
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        log.warn("请求访问：{}，认证失败，无法访问系统资源", request.getRequestURI());
        String data = JSON.toJSONString(Result.fail().code(HttpStatus.UNAUTHORIZED).msg("未授权禁止访问"));
        response.setStatus(200);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().print(data);
    }
}