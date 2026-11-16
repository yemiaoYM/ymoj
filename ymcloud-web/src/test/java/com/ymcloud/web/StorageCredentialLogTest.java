package com.ymcloud.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.utils.ExceptionLogUtils;
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

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * @Description: 验证存储请求无机械日志，系统故障保留原因且响应和日志不泄露凭据
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
            assertTrue(capture.list.isEmpty());
            assertFalse(capture.list.stream().anyMatch(event -> event.getFormattedMessage().contains(credential)));
            assertTrue(capture.list.stream().allMatch(event -> event.getThrowableProxy() == null));
            verifyNoInteractions(service);
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    /**
     * 正常和业务拒绝请求不记录机械动作日志或凭据
     * @throws Exception MVC 请求执行失败
     */
    @Test
    void ordinaryStorageRequestsDoNotProduceActionLogs() throws Exception {
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
            assertTrue(capture.list.isEmpty());
            assertFalse(capture.list.stream().anyMatch(event -> event.getFormattedMessage().contains("private-")));
            assertTrue(capture.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    /**
     * 保留原始故障原因，在最终 HTTP 边界只记录一次不含敏感正文的根因调用栈
     * @throws Exception MVC 请求执行失败
     */
    @Test
    void systemFailureKeepsCauseButNeverSerializesOrLogsItsSecrets() throws Exception {
        IOException cause = new IOException("private-secret-key SQL /server/private/path");
        cause.addSuppressed(new IOException("private-secret-key suppressed"));
        FileException failure = new FileException("存储配置读取失败", cause);
        doThrow(failure).when(service).create(any());
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
                            .content("{}"))
                    .andExpect(jsonPath("$.code").value(HttpStatus.ERROR))
                    .andExpect(jsonPath("$.msg").value("存储配置读取失败"))
                    .andExpect(jsonPath("$.cause").doesNotExist())
                    .andExpect(jsonPath("$.stackTrace").doesNotExist());
            assertSame(cause, failure.getCause());
            assertEquals(1, capture.list.size());
            ILoggingEvent event = capture.list.get(0);
            assertEquals("ERROR", event.getLevel().toString());
            assertFalse(event.getFormattedMessage().contains("private-secret-key"));
            assertEquals(IOException.class.getName(), event.getThrowableProxy().getMessage());
            Throwable diagnostic = ExceptionLogUtils.forLogging(failure);
            assertArrayEquals(cause.getStackTrace(), diagnostic.getStackTrace());
            assertEquals(0, diagnostic.getSuppressed().length);
            assertEquals(1, cause.getSuppressed().length);
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
