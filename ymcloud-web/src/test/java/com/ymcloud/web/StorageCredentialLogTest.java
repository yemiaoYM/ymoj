package com.ymcloud.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.web.controller.admin.common.AdminStorageConfigController;
import com.ymcloud.web.handler.GlobalExceptionHandler;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 验证存储接口动作日志与请求解析失败均不泄露凭据
 */
@ExtendWith(MockitoExtension.class)
class StorageCredentialLogTest {

    @Mock
    private AdminStorageConfigService service;

    @InjectMocks
    private AdminStorageConfigController controller;

    /**
     * JSON 解析失败不把凭据片段写入日志或响应。
     * @throws Exception MVC 请求执行失败
     */
    @Test
    void malformedStorageCredentialJsonNeverLeaksParserToken() throws Exception {
        String credential = "dummy_sensitive_parser_token";
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
        try {
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
            mvc.perform(post("/admin/storage/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"secretKey\":" + credential + "}"))
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("请求参数格式不正确，请检查输入数据"));
            assertTrue(capture.list.stream().anyMatch(event -> event.getFormattedMessage().contains("请求体无法读取")));
            assertFalse(capture.list.stream().anyMatch(event -> event.getFormattedMessage().contains(credential)));
            assertTrue(capture.list.stream().allMatch(event -> event.getThrowableProxy() == null));
            verifyNoInteractions(service);
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    /**
     * 正常和业务拒绝请求都只记录动作，不输出 DTO、凭据或伪造成功。
     * @throws Exception MVC 请求执行失败
     */
    @Test
    void storageActionLogsExcludeCredentialsAndDoNotClaimSuccess() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(AdminStorageConfigController.class);
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
        try {
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
            String body = "{\"key\":\"private-profile\",\"accessKey\":\"private-access-key\","
                    + "\"secretKey\":\"private-secret-key\"}";
            mvc.perform(post("/admin/storage/config").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(HttpStatus.SUCCESS));
            doThrow(new FileException("存储配置不可用", HttpStatus.BAD_REQUEST)).when(service).create(any());
            mvc.perform(post("/admin/storage/config").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(HttpStatus.BAD_REQUEST));
            verify(service, times(2)).create(any());
            assertEquals(2, capture.list.size());
            assertTrue(capture.list.stream().allMatch(event -> "创建存储配置".equals(event.getFormattedMessage())));
            assertFalse(capture.list.stream().anyMatch(event -> event.getFormattedMessage().contains("private-")));
            assertTrue(capture.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
