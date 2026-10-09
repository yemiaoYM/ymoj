package com.ymcloud.web;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.config.SecurityConfig;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.filter.AuthenticationEntryPointImpl;
import com.ymcloud.core.security.filter.CustomAccessDeniedHandler;
import com.ymcloud.core.security.filter.JwtAuthenticationTokenFilter;
import com.ymcloud.core.security.filter.LogoutSuccessHandlerImpl;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.common.AdminRoleController;
import com.ymcloud.web.service.admin.AdminRoleService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 使用项目真实安全链和角色控制器验证接口权限，无外部服务依赖
 */
public class RbacHttpSecurityTest {

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AdminRoleController.class, FuturePermissionController.class, AuthenticationEntryPointImpl.class,
            CustomAccessDeniedHandler.class, LogoutSuccessHandlerImpl.class})
    static class TestConfig {
        @Bean
        AdminRoleService adminRoleService() {
            return mock(AdminRoleService.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }
    }

    /**
     * 模拟尚未配置进数据库的新权限
     */
    @RestController
    @RequestMapping("/admin/rbac-test/future")
    static class FuturePermissionController {

        @PreAuthorize("@ss.hasPermi('future:feature:write')")
        @GetMapping
        public Result<Void> futureFeature() {
            return Result.ok();
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AdminRoleService service;

    @BeforeEach
    void setup() throws Exception {
        JwtAuthenticationTokenFilter jwtFilter = mock(JwtAuthenticationTokenFilter.class);
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            String permissions = request.getHeader("Mock-Permissions");
            if (permissions != null) {
                var authorities = Arrays.stream(permissions.split(","))
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                User user = new User();
                String userId = request.getHeader("Mock-User-Id");
                user.setId(userId == null ? 9L : Long.valueOf(userId));
                user.setStatus(UserStatus.OK);
                LoginUser loginUser = new LoginUser(user, List.of("ADMIN"), List.of(permissions.split(",")));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities));
            }
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.addBeanFactoryPostProcessor(beanFactory -> {
            beanFactory.registerSingleton("jwtAuthenticationTokenFilter", jwtFilter);
            beanFactory.registerSingleton("tokenService", mock(TokenService.class));
        });
        context.register(TestConfig.class);
        context.refresh();
        service = context.getBean(AdminRoleService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/admin/role/list")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void endpointPermissionAloneCannotBypassAdminEntryPermission() throws Exception {
        mvc.perform(get("/admin/role/list").header("Mock-Permissions", "system:role:list"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
    }

    @Test
    void adminEntryAloneCannotBypassSpecificEndpointPermission() throws Exception {
        mvc.perform(get("/admin/role/list").header("Mock-Permissions", "admin:access"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
    }

    @Test
    void customRoleCanAccessEndpointWithBothPermissions() throws Exception {
        mvc.perform(get("/admin/role/list").header("Mock-Permissions", "admin:access,system:role:list"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).queryList(any());
    }

    @Test
    void invalidPermissionIdsCannotReachAssignmentService() throws Exception {
        mvc.perform(put("/admin/role/4/auth").header("Mock-Permissions", "admin:access,system:role:auth")
                .contentType("application/json").content("{\"authIds\":[-1]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void superAdminWildcardAllowsCurrentAndFuturePermissionsWithoutRoleMappings() throws Exception {
        mvc.perform(get("/admin/role/list")
                        .header("Mock-User-Id", "1")
                        .header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).queryList(any());
        mvc.perform(get("/admin/rbac-test/future")
                        .header("Mock-User-Id", "1")
                        .header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void ordinaryAdminRoleAndWildcardCannotBypassPermissionChecks() throws Exception {
        mvc.perform(get("/admin/rbac-test/future")
                        .header("Mock-Permissions", "admin:access,ROLE_ADMIN,*:*:*"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
    }
}
