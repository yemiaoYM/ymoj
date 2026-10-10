package com.ymcloud.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ymcloud.web.controller.admin.common.AdminStorageConfigController;
import com.ymcloud.web.handler.GlobalExceptionHandler;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 真实请求解析失败时不把未加引号的凭据片段写入日志或返回响应。
 */
class StorageCredentialLogTest {

    @Test
    void malformedStorageCredentialJsonNeverLeaksParserToken() throws Exception {
        String credential = "dummy_sensitive_parser_token";
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
        AdminStorageConfigService service = mock(AdminStorageConfigService.class);
        try {
            var mvc = MockMvcBuilders.standaloneSetup(new AdminStorageConfigController(service))
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
}
