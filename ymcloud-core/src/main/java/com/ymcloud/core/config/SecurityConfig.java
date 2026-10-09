package com.ymcloud.core.config;

import com.ymcloud.core.security.filter.AuthenticationEntryPointImpl;
import com.ymcloud.core.security.filter.CustomAccessDeniedHandler;
import com.ymcloud.core.security.filter.JwtAuthenticationTokenFilter;
import com.ymcloud.core.security.filter.LogoutSuccessHandlerImpl;
import com.ymcloud.core.security.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * @PackageName: com.ymcloud.core.config
 * @ClassName: SecurityConfig
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  21:48
 * @Description: SpringSecurity配置类
 */
@Configuration
@EnableMethodSecurity(securedEnabled = true)
@Import(PermissionService.class)
public class SecurityConfig {

    /**
     * 自定义jwt认证过滤
     */
    @Autowired
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;
    /**
     * 认证失败处理
     */
    @Autowired
    private AuthenticationEntryPointImpl authenticationEntryPoint;
    /**
     * 权限不足访问失败
     */
    @Autowired
    private CustomAccessDeniedHandler accessDeniedHandler;
    /**
     * 退出登录成功
     */
    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    @Autowired
    private PermissionService permissionService;

    /**
     * 强散列哈希加密实现
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 身份验证实现
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * SpringSecurity安全链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        //配置关闭csrf攻击
        //利用用户的登录状态，诱导用户发起恶意请求
        http.csrf(AbstractHttpConfigurer::disable);
        // 注解标记允许匿名访问的url
        http.authorizeHttpRequests(request->{
           request.requestMatchers("/login","/register","/captchaImage","/forget-code","/forget-password").permitAll()
                   .requestMatchers("/public/**").permitAll()
                   .requestMatchers("/files/public/**").permitAll()
                   .requestMatchers("/admin/**").access((authentication, context) ->
                           new AuthorizationDecision(permissionService.hasPermi(authentication.get(), "admin:access")))
                   // 除上面外的所有请求全部需要鉴权认证
                   .anyRequest().authenticated();
        });
        //认证失败处理
        http.exceptionHandling(exception->
                exception.authenticationEntryPoint(authenticationEntryPoint) //401
                        .accessDeniedHandler(accessDeniedHandler)            //403
        );
        //不使用session
        http.sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        //logout
        http.logout(logout->logout.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler));
        //jwt
        http.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        //cors
        http.cors(Customizer.withDefaults());
        return http.build();
    }

}
