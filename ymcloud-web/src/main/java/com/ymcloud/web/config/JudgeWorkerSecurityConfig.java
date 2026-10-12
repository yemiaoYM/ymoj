package com.ymcloud.web.config;

import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

/**
 * Worker 数据入口只接受专用服务身份，不复用管理员或用户的登录权限。
 */
@Configuration
@EnableConfigurationProperties(JudgeWorkerIdentityProperties.class)
public class JudgeWorkerSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain judgeWorkerFilterChain(HttpSecurity http,
                                                     JudgeWorkerIdentityProperties properties) throws Exception {
        http.securityMatcher("/internal/judge-worker/**");
        http.csrf(AbstractHttpConfigurer::disable);
        http.requestCache(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(request -> request.anyRequest().hasAuthority("ROLE_JUDGE_WORKER"));
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, failure) -> response.setStatus(401))
                .accessDeniedHandler((request, response, failure) -> response.setStatus(403)));
        http.addFilterBefore(new WorkerTokenFilter(properties), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static final class WorkerTokenFilter extends OncePerRequestFilter {

        private final JudgeWorkerIdentityProperties properties;

        WorkerTokenFilter(JudgeWorkerIdentityProperties properties) {
            this.properties = properties;
        }

        @Override
        protected boolean shouldNotFilterAsyncDispatch() {
            // StreamingResponseBody 完成后会再次经过安全链；无状态服务身份需重新校验。
            return false;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            // 不向调用方暴露服务是否已配置，也不接受多个同名身份头。
            List<String> values = Collections.list(request.getHeaders(JudgeProtocol.SERVICE_TOKEN_HEADER));
            String expected = properties.getToken();
            String supplied = values.size() == 1 ? values.get(0) : null;
            if (!isConfiguredIdentityValid(expected) || supplied == null || supplied.length() > 512
                    || !MessageDigest.isEqual(hash(expected), hash(supplied))) {
                // 直接结束服务响应，避免容器分派到普通用户的 /error 后把 HTTP 状态改为 200。
                response.setStatus(401);
                return;
            }
            if (!"GET".equals(request.getMethod())) {
                response.setStatus(405);
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "judge-worker", null, List.of(new SimpleGrantedAuthority("ROLE_JUDGE_WORKER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        }

        private static boolean isConfiguredIdentityValid(String identity) {
            return identity != null && identity.length() >= 32 && identity.length() <= 512
                    && identity.chars().allMatch(character -> character > 32 && character < 127);
        }

        private static byte[] hash(String value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }
}
