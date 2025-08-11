package com.ymcloud.core.security.filter;

import cn.hutool.core.util.StrUtil;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.core.websocket.HeaderMapRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //通过request的url判断是否为websocket请求
        //如果为websocket请求，先处理Authorization
        if(request.getRequestURI().contains("/ws/")){
            HeaderMapRequestWrapper requestWrapper = new HeaderMapRequestWrapper(request);
            //修改header信息 将websocket请求中的Sec-WebSocket-Protocol值取出处理后放入认证参数key
            //此处需要根据自己的认证方式进行修改  Authorization
            requestWrapper.addHeader("Authorization", request.getHeader("Sec-WebSocket-Protocol"));
            request = requestWrapper;
        }
        //先获取loginUser
        LoginUser loginUser = tokenService.getLoginUser(request);
        //loginUser不为空，且当前没有认证信息（防止重复）
        if(loginUser != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.info("当前登录用户：{}", loginUser);
            tokenService.verifyToken(loginUser);
            //把loginUser封装为Spring Security的认证对象
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            //设置认证详细
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            //把认证信息写入上下文，Spring Security识别已经登录
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
        filterChain.doFilter(request, response);
    }
}
