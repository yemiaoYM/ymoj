package com.ymcloud.web;

import com.ymcloud.pojo.dto.admin.SystemSettingDTO;
import com.ymcloud.pojo.vo.common.SystemSettingVO;
import com.ymcloud.web.controller.admin.common.AdminSystemSettingController;
import com.ymcloud.web.service.common.SystemSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证系统配置接口的实际方法权限，不启动外部服务
 */
public class SystemSettingPermissionTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private AnnotationConfigApplicationContext context;
    private SystemSettingService systemSettingService;
    private AdminSystemSettingController controller;

    @BeforeEach
    void setup() {
        systemSettingService = mock(SystemSettingService.class);
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class, AdminSystemSettingController.class);
        context.registerBean(SystemSettingService.class, () -> systemSettingService);
        context.refresh();
        controller = context.getBean(AdminSystemSettingController.class);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void anonymousAndOrdinaryUsersCannotReadOrWriteSettings() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.getSystemSetting());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "", "ROLE_USER"));
        assertThrows(AccessDeniedException.class, () -> controller.getSystemSetting());
        assertThrows(AccessDeniedException.class, () -> controller.updateSystemSetting(new SystemSettingDTO()));
        verifyNoInteractions(systemSettingService);
    }

    @Test
    void readPermissionDoesNotGrantWritePermission() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("viewer", "", "system:setting:list"));
        when(systemSettingService.getSystemSetting()).thenReturn(new SystemSettingVO());
        assertDoesNotThrow(() -> controller.getSystemSetting());
        assertThrows(AccessDeniedException.class, () -> controller.updateSystemSetting(new SystemSettingDTO()));
        verify(systemSettingService, never()).updateSystemSetting(any());
    }

    @Test
    void editPermissionAllowsSaving() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "", "system:setting:list", "system:setting:edit"));
        SystemSettingDTO settingDTO = new SystemSettingDTO();
        settingDTO.setRegisterEnabled(false);
        assertDoesNotThrow(() -> controller.updateSystemSetting(settingDTO));
        verify(systemSettingService).updateSystemSetting(settingDTO);
    }
}
