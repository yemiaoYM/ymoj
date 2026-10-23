package com.ymcloud.core.security.filter;

import com.alibaba.fastjson2.JSON;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.core.security.filter
 * @ClassName: CustomAccessDeniedHandler
 * @Author: Yemiao
 * @CreateTime: 2025-07-21  19:05
 * @Description: 权限不足访问失败
 */
@Component
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.warn("请求访问：{}，权限不足，无法访问该资源", request.getRequestURI());
        String data = JSON.toJSONString(Result.fail().code(HttpStatus.FORBIDDEN).msg("权限不足，无法访问该资源"));
        response.setStatus(HttpStatus.SUCCESS);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().print(data);
    }
}
