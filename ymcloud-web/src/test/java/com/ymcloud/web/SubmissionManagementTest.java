package com.ymcloud.web;

import com.ymcloud.common.exception.submission.SubmissionException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.pojo.dto.SubmissionExecutionDTO;
import com.ymcloud.pojo.dto.SubmissionStatusDTO;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.AdminSubmissionController;
import com.ymcloud.web.service.oj.JudgeService;
import com.ymcloud.web.service.oj.impl.JudgeServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-30
 * @Description: 提交管理的结构校验、HTTP 契约和超级管理员授权回归
 */
class SubmissionManagementTest {

    /**
     * 启用真实方法权限代理
     */
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    /**
     * 非超级管理员即使有管理权限，也不能调用重评或手动状态服务
     */
    @Test
    void ordinaryAdministratorCannotManageSubmissions() {
        authenticate(2L, List.of("*:*:*", "system:submission:rejudge", "system:submission:edit"));
        try {
            JudgeServiceImpl service = new JudgeServiceImpl();
            SubmissionException rejudge = assertThrows(SubmissionException.class, () -> service.rejudge(10L, null));
            SubmissionException manual = assertThrows(SubmissionException.class,
                    () -> service.updateSubmissionStatus(10L, new SubmissionStatusDTO()));
            assertEquals(403, rejudge.getCode());
            assertEquals(403, manual.getCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 校验最终状态、部分分数及历史执行标识的允许范围
     */
    @Test
    void validatesTerminalStatusScoreAndExecutionIdentity() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            SubmissionStatusDTO request = new SubmissionStatusDTO();
            for (int status : List.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 10)) {
                request.setStatus(status);
                assertTrue(validator.validate(request).isEmpty());
            }
            for (int status : List.of(-10, 5, 6, 7, 9, 15, 999)) {
                request.setStatus(status);
                assertFalse(validator.validate(request).isEmpty());
            }
            request.setStatus(8);
            for (Integer score : new Integer[]{null, 0, 100, -1}) {
                request.setScore(score);
                assertFalse(validator.validate(request).isEmpty());
            }
            request.setScore(35);
            assertTrue(validator.validate(request).isEmpty());
            request.setExecutionId("bad-execution");
            assertFalse(validator.validate(request).isEmpty());
            request.setExecutionId(UUID.randomUUID().toString());
            assertTrue(validator.validate(request).isEmpty());
            request.setStatus(0);
            assertFalse(validator.validate(request).isEmpty());
        }
    }

    /**
     * 新权限无需给超级管理员逐项分配，匿名及普通角色无法绕过方法权限
     */
    @Test
    void methodSecurityAllowsSuperAdminAndRejectsAnonymousAndWildcardRole() {
        JudgeService service = mock(JudgeService.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(MethodSecurityConfig.class, AdminSubmissionController.class, PermissionService.class);
            context.registerBean(JudgeService.class, () -> service);
            context.refresh();
            var controller = context.getBean(AdminSubmissionController.class);
            assertThrows(AccessDeniedException.class, () -> controller.rejudge(10L, new SubmissionExecutionDTO()));
            authenticate(2L, List.of("*:*:*"));
            assertThrows(AccessDeniedException.class, () -> controller.rejudge(10L, new SubmissionExecutionDTO()));
            assertThrows(AccessDeniedException.class, () -> controller.updateStatus(10L, new SubmissionStatusDTO()));
            verifyNoInteractions(service);
            authenticate(1L, List.of());
            controller.rejudge(10L, new SubmissionExecutionDTO());
            SubmissionStatusDTO request = new SubmissionStatusDTO();
            request.setStatus(0);
            controller.updateStatus(10L, request);
            verify(service).rejudge(10L, null);
            verify(service).updateSubmissionStatus(10L, request);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * HTTP 入口必须触发 DTO 校验，非法状态不能进入业务层
     * @throws Exception MockMvc 请求失败时向测试框架报告
     */
    @Test
    void httpBindingRejectsInvalidCommandsAndAcceptsLegacyRejudge() throws Exception {
        JudgeService service = mock(JudgeService.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AdminSubmissionController.class);
            context.registerBean(JudgeService.class, () -> service);
            context.refresh();
            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(AdminSubmissionController.class)).build();
            authenticate(1L, List.of());
            mvc.perform(put("/admin/submission/10/status").contentType("application/json").content("{\"status\":5}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/admin/submission/10/status").contentType("application/json").content("{\"status\":8,\"score\":101}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/admin/submission/10/rejudge").contentType("application/json").content("{\"executionId\":\"bad\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(service);
            mvc.perform(post("/admin/submission/10/rejudge").contentType("application/json").content("{\"executionId\":null}"))
                    .andExpect(jsonPath("$.code").value(200));
            verify(service).rejudge(10L, null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 为当前线程设置具有指定权限的可用用户
     * @param id 用户编号
     * @param permissions 实际权限列表
     */
    private void authenticate(Long id, List<String> permissions) {
        User user = new User();
        user.setId(id);
        user.setStatus(UserStatus.OK);
        LoginUser login = new LoginUser(user, List.of("ADMIN"), permissions);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                login, null, permissions.stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
