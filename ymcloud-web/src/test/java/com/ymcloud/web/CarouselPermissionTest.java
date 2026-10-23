package com.ymcloud.web;

import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.web.controller.admin.common.AdminCarouselController;
import com.ymcloud.web.service.common.CarouselSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 验证轮播入口的真实方法权限及 HTTP 参数校验
 */
class CarouselPermissionTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    /** 未登录不能读取；只读权限不能保存或上传；编辑权限才能进入写服务。 */
    @Test
    void enforcesReadAndWritePermissions() {
        CarouselSettingService service = mock(CarouselSettingService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(MethodSecurityConfig.class, AdminCarouselController.class, PermissionService.class);
            context.registerBean(CarouselSettingService.class, () -> service);
            context.refresh();
            var controller = context.getBean(AdminCarouselController.class);
            assertThrows(AccessDeniedException.class, controller::get);
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("viewer", "", "system:setting:list"));
            assertDoesNotThrow(controller::get);
            assertThrows(AccessDeniedException.class, () -> controller.save(new CarouselSettingDTO()));
            assertThrows(AccessDeniedException.class, () -> controller.upload(null));
            verify(service, never()).save(any());
            verify(service, never()).upload(any());
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("editor", "", "system:setting:edit"));
            assertDoesNotThrow(() -> controller.save(new CarouselSettingDTO()));
            assertDoesNotThrow(() -> controller.upload(null));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * MVC 绑定拒绝缺少列表和非法元素，空列表允许隐藏轮播。
     * @throws Exception MVC 执行失败
     */
    @Test
    void validatesHttpRequestBeforeService() throws Exception {
        CarouselSettingService service = mock(CarouselSettingService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminCarouselController(service)).build();
        for (String body : new String[]{"{}", "{\"fileIds\":[null]}", "{\"fileIds\":[\"0\"]}"}) {
            mvc.perform(put("/admin/system-setting/carousel").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        mvc.perform(put("/admin/system-setting/carousel").contentType("application/json").content("{\"fileIds\":[]}"))
                .andExpect(status().isOk());
        verify(service).save(any());
    }
}
