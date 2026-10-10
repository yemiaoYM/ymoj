package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.core.config.SecurityConfig;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.filter.AuthenticationEntryPointImpl;
import com.ymcloud.core.security.filter.CustomAccessDeniedHandler;
import com.ymcloud.core.security.filter.JwtAuthenticationTokenFilter;
import com.ymcloud.core.security.filter.LogoutSuccessHandlerImpl;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.common.AdminStorageMigrationController;
import com.ymcloud.web.controller.admin.problem.AdminProblemController;
import com.ymcloud.web.controller.oj.PublicFileController;
import com.ymcloud.web.handler.GlobalExceptionHandler;
import com.ymcloud.web.service.admin.AdminProblemService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实安全链及方法鉴权，验证存储代理、迁移和测试数据接口权限边界。
 */
class StorageHttpSecurityTest {

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, PublicFileController.class, AdminStorageMigrationController.class,
            AdminProblemController.class, GlobalExceptionHandler.class, AuthenticationEntryPointImpl.class,
            CustomAccessDeniedHandler.class, LogoutSuccessHandlerImpl.class})
    static class TestConfig {

        @Bean
        StoredFileService storedFileService() {
            return mock(StoredFileService.class);
        }

        @Bean
        FileEntityService fileEntityService() {
            return mock(FileEntityService.class);
        }

        @Bean
        TestDataService testDataService() {
            return mock(TestDataService.class);
        }

        @Bean
        AdminProblemService adminProblemService() {
            return mock(AdminProblemService.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private StoredFileService files;
    private TestDataService testData;
    private AdminProblemService problems;
    private LambdaQueryChainWrapper<File> publicMetadata;

    @BeforeEach
    @SuppressWarnings("unchecked")
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
                LoginUser principal = new LoginUser(user, List.of("ADMIN"), List.of(permissions.split(",")));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
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
        files = context.getBean(StoredFileService.class);
        testData = context.getBean(TestDataService.class);
        problems = context.getBean(AdminProblemService.class);
        publicMetadata = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(publicMetadata.eq(any(), any())).thenReturn(publicMetadata);
        when(publicMetadata.and(any())).thenReturn(publicMetadata);
        when(context.getBean(FileEntityService.class).lambdaQuery()).thenReturn(publicMetadata);
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
    void publicImageCanBeDownloadedAnonymouslyWithSafeHeaders() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        File file = publicFile("avatar.png", "image/png", bytes.length);
        when(files.resolvePublicUrl("/files/public/seaweed/avatar.png")).thenReturn(file);
        when(files.open(file)).thenReturn(new ByteArrayInputStream(bytes));

        mvc.perform(get("/files/public/seaweed/avatar.png"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    void publicHtmlIsServedAsAttachmentAndCannotBeSniffedAsActiveContent() throws Exception {
        byte[] bytes = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        File file = publicFile("page.html", "text/html", bytes.length);
        when(files.resolvePublicUrl("/files/public/seaweed/page.html")).thenReturn(file);
        when(files.open(file)).thenReturn(new ByteArrayInputStream(bytes));

        mvc.perform(get("/files/public/seaweed/page.html"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    @Test
    void historicalPublicUrlUsesConfinedLocalCopyInsteadOfAnotherNodesAbsolutePath() throws Exception {
        byte[] bytes = new byte[]{3, 4, 5};
        File file = publicFile("avatar.jpg", "image/jpeg", bytes.length);
        file.setStorageType(0);
        file.setStoragePath("/home/ymcloud/files/public/image/avatar.jpg");
        when(files.resolvePublicUrl("/files/public/image/avatar.jpg")).thenReturn(file);
        when(files.openLegacyPublic("image/avatar.jpg")).thenReturn(new ByteArrayInputStream(bytes));

        mvc.perform(get("/files/public/image/avatar.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(files, never()).open(file);
    }

    @Test
    void privateRecordWithHistoricalPublicUrlCannotFallBackToLocalBytes() throws Exception {
        when(publicMetadata.exists()).thenReturn(true);

        mvc.perform(get("/files/public/image/private.txt"))
                .andExpect(jsonPath("$.code").value(404));

        verifyNoInteractions(files);
    }

    @Test
    void testDataCannotBeServedPubliclyEvenWhenLegacyRecordIsMarkedPublic() throws Exception {
        File file = publicFile("secret.in", "text/plain", 1);
        file.setBizType(FileBizType.TEST_DATA);
        when(files.resolvePublicUrl("/files/public/secret.in")).thenReturn(file);

        mvc.perform(get("/files/public/secret.in"))
                .andExpect(jsonPath("$.code").value(404));

        verify(files, never()).open(any(File.class));
        verify(files, never()).openLegacyPublic(any());
    }

    @Test
    void privateFilesAndAdminInterfacesRejectAnonymousRequests() throws Exception {
        mvc.perform(get("/files/private/problem/10/1.in"))
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/admin/storage/migration/files"))
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/admin/problem/10/testcases"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(files, testData, problems);
    }

    @Test
    void migrationPermissionCannotBypassAdminEntryPermission() throws Exception {
        mvc.perform(get("/admin/storage/migration/files")
                        .header("Mock-Permissions", "file:migration:query"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(files);
    }

    @Test
    void adminEntryPermissionCannotBypassMigrationEndpointPermission() throws Exception {
        mvc.perform(get("/admin/storage/migration/files").header("Mock-Permissions", "admin:access"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/admin/storage/migration/files/5").header("Mock-Permissions", "admin:access"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(files);
    }

    @Test
    void ordinaryAdminWildcardCannotReadOrExecuteStorageAndTestDataMigration() throws Exception {
        String permissions = "admin:access,ROLE_ADMIN,*:*:*";
        mvc.perform(get("/admin/storage/migration/files").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/admin/storage/migration/files/5").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/admin/problem/10/testcases/migration").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(files, testData);
    }

    @Test
    void superAdminIdOneCanUseNewPermissionsAndMigrationDefaultsToInspection() throws Exception {
        mvc.perform(get("/admin/storage/migration/files")
                        .header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/admin/storage/migration/files/5")
                        .header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/admin/storage/migration/files/5").param("apply", "true")
                        .header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/admin/problem/10/testcases/migration")
                        .header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200));
        verify(files).legacyInventory();
        verify(files).migrateLocal(5L, false);
        verify(files).migrateLocal(5L, true);
        verify(testData).migrateLegacy(10L, true);
    }

    @Test
    void fileMigrationQueryAndExecutionPermissionsAreIndependent() throws Exception {
        mvc.perform(get("/admin/storage/migration/files")
                        .header("Mock-Permissions", "admin:access,file:migration:query"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/admin/storage/migration/files/5").param("apply", "true")
                        .header("Mock-Permissions", "admin:access,file:migration:query"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/admin/storage/migration/files/5").param("apply", "true")
                        .header("Mock-Permissions", "admin:access,file:migration:execute"))
                .andExpect(jsonPath("$.code").value(200));
        verify(files).legacyInventory();
        verify(files).migrateLocal(5L, true);
    }

    @Test
    void testDataQueryPermissionCannotPublishUploadOrDelete() throws Exception {
        String permissions = "admin:access,problem:testcase:query";
        mvc.perform(get("/admin/problem/10/testcases").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/admin/problem/10/testcases/migration").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/admin/problem/10/testcases/migration").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(multipart("/admin/problem/10/testcases/upload").file(inputFile())
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete("/admin/problem/10/testcases")
                        .contentType(MediaType.APPLICATION_JSON).content("[\"1.in\"]")
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verify(problems).getTestCaseFiles(10L);
        verify(testData).migrateLegacy(10L, false);
        verify(testData, never()).migrateLegacy(10L, true);
        verify(problems, never()).uploadTestCaseFile(any(), any());
        verify(problems, never()).deleteTestCaseFiles(any(), any());
    }

    @Test
    void uploadPermissionAllowsPublishButCannotQueryOrDelete() throws Exception {
        String permissions = "admin:access,problem:testcase:add";
        mvc.perform(multipart("/admin/problem/10/testcases/upload").file(inputFile())
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/admin/problem/10/testcases/migration").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/admin/problem/10/testcases").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete("/admin/problem/10/testcases")
                        .contentType(MediaType.APPLICATION_JSON).content("[\"1.in\"]")
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verify(problems).uploadTestCaseFile(eq(10L), any());
        verify(testData).migrateLegacy(10L, true);
        verify(problems, never()).getTestCaseFiles(any());
        verify(problems, never()).deleteTestCaseFiles(any(), any());
    }

    @Test
    void removalPermissionAllowsDeleteWithoutGrantingReadOrUpload() throws Exception {
        String permissions = "admin:access,problem:testcase:remove";
        mvc.perform(delete("/admin/problem/10/testcases")
                        .contentType(MediaType.APPLICATION_JSON).content("[\"1.in\"]")
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/admin/problem/10/testcases").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(multipart("/admin/problem/10/testcases/upload").file(inputFile())
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verify(problems).deleteTestCaseFiles(10L, List.of("1.in"));
        verify(problems, never()).getTestCaseFiles(any());
        verify(problems, never()).uploadTestCaseFile(any(), any());
    }

    private File publicFile(String name, String contentType, long size) {
        File file = new File();
        file.setId(5L);
        file.setIsPublic(1);
        file.setFileName(name);
        file.setFileType(contentType);
        file.setFileSize(size);
        return file;
    }

    private MockMultipartFile inputFile() {
        return new MockMultipartFile("file", "1.in", "text/plain", "1\n".getBytes(StandardCharsets.UTF_8));
    }
}
