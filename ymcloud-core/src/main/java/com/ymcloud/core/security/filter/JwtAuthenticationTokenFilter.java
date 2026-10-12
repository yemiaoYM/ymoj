package com.ymcloud.core.security.filter;

import com.alibaba.fastjson2.JSON;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.result.Result;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.AuthorizationService;
import com.ymcloud.core.security.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @PackageName: com.ymcloud.core.security.filter
 * @ClassName: JwtAuthenticationTokenFilter
 * @Author: Yemiao
 * @CreateTime: 2025-07-21  10:37
 * @Description: token过滤器 验证token有效性
 */
@Component
@Slf4j
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 专用安全链接管 Worker 身份，不加载用户权限或刷新用户会话。
        String path = request.getServletPath();
        return path.startsWith("/internal/judge-worker/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            //先获取loginUser
            LoginUser loginUser = tokenService.getLoginUser(request);
            //loginUser不为空，且当前没有认证信息（防止重复）
            if (loginUser != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (!authorizationService.refreshAuthorization(loginUser)) {
                    tokenService.delLoginUser(loginUser.getToken());
                } else {
                    log.debug("当前登录用户：{}", loginUser.getUsername());
                    tokenService.verifyToken(loginUser);
                    //把loginUser封装为Spring Security的认证对象
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            loginUser, null, loginUser.getAuthorities());
                    //设置认证详细
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    //把认证信息写入上下文，Spring Security识别已经登录
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        } catch (DataAccessException e) {
            log.error("认证依赖服务异常，请求URI：{}", request.getRequestURI(), e);
            String data = JSON.toJSONString(Result.fail().code(HttpStatus.SERVICE_UNAVAILABLE)
                    .msg("登录服务暂时不可用，请稍后重试"));
            response.setStatus(200);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().print(data);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
