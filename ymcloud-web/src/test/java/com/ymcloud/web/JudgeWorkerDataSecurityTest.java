package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.TestDataManifest;
import com.ymcloud.pojo.entity.TestDataVersion;
import com.ymcloud.web.config.JudgeWorkerIdentityProperties;
import com.ymcloud.web.config.JudgeWorkerSecurityConfig;
import com.ymcloud.web.controller.internal.JudgeWorkerDataController;
import com.ymcloud.web.controller.internal.JudgeWorkerDataExceptionHandler;
import com.ymcloud.web.service.internal.impl.JudgeWorkerDataServiceImpl;
import com.ymcloud.web.service.oj.TestDataService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 真实服务安全链验证身份隔离、清单原始字节和版本文件引用约束
 */
class JudgeWorkerDataSecurityTest {

    private static final String TOKEN = "unit-test-service-identity-32-bytes-only";
    private static final String ROOT = "/internal/judge-worker/problems/11/versions/22";

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @EnableMethodSecurity
    @Import({JudgeWorkerSecurityConfig.class, JudgeWorkerDataController.class,
            JudgeWorkerDataExceptionHandler.class, JudgeWorkerDataServiceImpl.class})
    static class Config {

        @Bean
        TestDataService testDataService() {
            return mock(TestDataService.class);
        }

        @Bean
        TestDataVersionEntityService versionEntityService() {
            return mock(TestDataVersionEntityService.class);
        }

        @Bean
        SecurityFilterChain otherRequests(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            http.exceptionHandling(exception -> exception
                    .authenticationEntryPoint((request, response, failure) -> response.sendError(401)));
            return http.build();
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TestDataService data;
    private TestDataVersionEntityService versions;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        context.getBean(JudgeWorkerIdentityProperties.class).setToken(TOKEN);
        data = context.getBean(TestDataService.class);
        versions = context.getBean(TestDataVersionEntityService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void userLoginAndMissingIncorrectOrDuplicateServiceTokensCannotReadPrivateData() throws Exception {
        mvc.perform(get(ROOT + "/manifest"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""))
                .andExpect(result -> assertNull(result.getResponse().getErrorMessage(),
                        "Worker 身份失败须直接返回，不能触发普通用户错误页分派"));
        mvc.perform(get(ROOT + "/manifest").header("Authorization", "Bearer user-admin-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/manifest").header(JudgeProtocol.SERVICE_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/manifest").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN, TOKEN))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(data, versions);
    }

    @Test
    void disabledOrWeakIdentityFailsClosedAndServiceTokenCannotAuthenticateOtherRoutes() throws Exception {
        context.getBean(JudgeWorkerIdentityProperties.class).setToken("");
        mvc.perform(get(ROOT + "/manifest").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isUnauthorized());
        context.getBean(JudgeWorkerIdentityProperties.class).setToken("short");
        mvc.perform(get(ROOT + "/manifest").header(JudgeProtocol.SERVICE_TOKEN_HEADER, "short"))
                .andExpect(status().isUnauthorized());
        context.getBean(JudgeWorkerIdentityProperties.class).setToken(TOKEN);
        mvc.perform(get("/user/profile").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/manifest").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(result -> assertNull(result.getResponse().getErrorMessage()));
        verifyNoInteractions(data, versions);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsExactManifestBytesAfterValidationWithoutReserialization() throws Exception {
        String json = "{  \"problemId\":11, \"versionId\":22, \"entries\":[] }\n";
        TestDataVersion version = new TestDataVersion();
        version.setManifest(json);
        version.setManifestSha256(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))));
        LambdaQueryChainWrapper<TestDataVersion> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(versions.lambdaQuery()).thenReturn(query);
        when(query.one()).thenReturn(version);
        mvc.perform(get(ROOT + "/manifest").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().bytes(json.getBytes(StandardCharsets.UTF_8)))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-SHA256", version.getManifestSha256()))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(data).getManifest(11L, 22L);
    }

    @Test
    void onlyReferencedFileIdsAreDownloadableAndRawWhitespaceIsPreserved() throws Exception {
        byte[] bytes = "  input\r\n\n  ".getBytes(StandardCharsets.UTF_8);
        TestDataManifest manifest = new TestDataManifest();
        TestDataManifest.Entry entry = new TestDataManifest.Entry();
        entry.setInputFileId(33L);
        entry.setInput("/in/one.in");
        entry.setInputSize((long) bytes.length);
        entry.setInputSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        manifest.setEntries(List.of(entry));
        when(data.getManifest(11L, 22L)).thenReturn(manifest);
        mvc.perform(get(ROOT + "/files/99").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isNotFound());
        verify(data, never()).openFile(any(), any(), any());
        when(data.openFile(11L, 22L, "/in/one.in")).thenReturn(new ByteArrayInputStream(bytes));
        MvcResult result = mvc.perform(get(ROOT + "/files/33").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("X-Content-SHA256", entry.getInputSha256()))
                .andExpect(header().string("Content-Length", Integer.toString(bytes.length)))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void invalidIdsAreRejectedBeforeBusinessDataAccess() throws Exception {
        mvc.perform(get("/internal/judge-worker/problems/-1/versions/22/manifest")
                        .header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isNotFound());
        verifyNoInteractions(data, versions);
    }

    @Test
    void backendErrorsReturnRealStatusWithoutPrivateDetails() throws Exception {
        when(data.getManifest(11L, 22L)).thenThrow(new IllegalStateException("private-location-or-secret"));
        mvc.perform(get(ROOT + "/manifest").header(JudgeProtocol.SERVICE_TOKEN_HEADER, TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));
    }
}
