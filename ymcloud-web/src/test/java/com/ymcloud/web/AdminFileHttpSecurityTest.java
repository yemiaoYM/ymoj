package com.ymcloud.web;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.core.config.SecurityConfig;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.filter.AuthenticationEntryPointImpl;
import com.ymcloud.core.security.filter.CustomAccessDeniedHandler;
import com.ymcloud.core.security.filter.JwtAuthenticationTokenFilter;
import com.ymcloud.core.security.filter.LogoutSuccessHandlerImpl;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.common.AdminFileController;
import com.ymcloud.web.handler.GlobalExceptionHandler;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import com.ymcloud.web.service.admin.impl.AdminFileServiceImpl;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 HTTP 安全链、方法权限和文件业务权限共同执行，不连接外部存储或数据库。
 */
class AdminFileHttpSecurityTest {

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AdminFileController.class, AdminFileServiceImpl.class, FileAccessPolicy.class,
            GlobalExceptionHandler.class, AuthenticationEntryPointImpl.class, CustomAccessDeniedHandler.class,
            LogoutSuccessHandlerImpl.class})
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
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private StoredFileService storedFiles;
    private FileEntityService entities;
    private LambdaQueryChainWrapper<File> query;

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
                user.setUsername("file-manager");
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
        storedFiles = context.getBean(StoredFileService.class);
        entities = context.getBean(FileEntityService.class);
        query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.like(anyBoolean(), any(), any())).thenReturn(query);
        when(query.eq(anyBoolean(), any(), any())).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.and(any())).thenReturn(query);
        when(query.orderByDesc(any(SFunction.class))).thenReturn(query);
        when(query.last(anyString())).thenReturn(query);
        when(entities.lambdaQuery()).thenReturn(query);
        page(List.of());
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
    void anonymousRequestsAreRejectedBeforeBusinessOrStorageAccess() throws Exception {
        mvc.perform(get("/admin/file/list")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/admin/file/17/download")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(multipart("/admin/file/upload").file(upload()).param("isPublic", "1"))
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(delete("/admin/file/17")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(storedFiles);
        verify(entities, never()).lambdaQuery();
    }

    @Test
    void adminEntryAndEndpointPermissionCannotBeSubstitutedByRoleOrWildcard() throws Exception {
        mvc.perform(get("/admin/file/list").header("Mock-Permissions", "file:file:list"))
                .andExpect(jsonPath("$.code").value(403));
        for (String permissions : new String[]{"admin:access", "admin:access,ROLE_ADMIN,*:*:*,file:file:*"}) {
            mvc.perform(get("/admin/file/list").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
            mvc.perform(get("/admin/file/17/download").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
            mvc.perform(delete("/admin/file/17").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
        }
        verifyNoInteractions(storedFiles);
    }

    @Test
    void fileListExposesOnlySafeMetadataAndDoesNotGrantDownloadOrDeletion() throws Exception {
        page(List.of(file(FileBizType.GENERAL, true)));
        mvc.perform(get("/admin/file/list").header("Mock-Permissions", "admin:access,file:file:list"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].bizType").value(0))
                .andExpect(jsonPath("$.data.records[0].storageProfile").value("seaweed"))
                .andExpect(jsonPath("$.data.records[0].canDownload").value(false))
                .andExpect(jsonPath("$.data.records[0].canDelete").value(false))
                .andExpect(jsonPath("$.data.records[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].legacyPath").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].bucket").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].objectKey").doesNotExist());
    }

    @Test
    void privateMetadataAndDownloadsNeedSpecificPrivatePermission() throws Exception {
        File file = file(FileBizType.GENERAL, false);
        page(List.of(file));
        when(storedFiles.get(17L)).thenReturn(file);
        mvc.perform(get("/admin/file/list").header("Mock-Permissions", "admin:access,file:file:list"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/admin/file/17/download").header("Mock-Permissions", "admin:access,file:file:download"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/admin/file/list")
                        .header("Mock-Permissions", "admin:access,file:file:list,file:file:private"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].url").doesNotExist());
        verify(storedFiles, never()).open(any(File.class));
    }

    @Test
    void testcaseManagerCanReadTestDataButCannotReadOrdinaryPrivateFiles() throws Exception {
        File testData = file(FileBizType.TEST_DATA, true);
        page(List.of(testData));
        when(storedFiles.get(17L)).thenReturn(testData);
        byte[] bytes = "  中文\n".getBytes(StandardCharsets.UTF_8);
        when(storedFiles.open(testData)).thenReturn(new ByteArrayInputStream(bytes));
        String permissions = "admin:access,problem:testcase:query";
        mvc.perform(get("/admin/file/list").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].bizType").value(5))
                .andExpect(jsonPath("$.data.records[0].isPublic").value(0))
                .andExpect(jsonPath("$.data.records[0].url").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].canDownload").value(true))
                .andExpect(jsonPath("$.data.records[0].canDelete").value(false));
        mvc.perform(get("/admin/file/17/download").header("Mock-Permissions", permissions))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
        when(storedFiles.get(17L)).thenReturn(file(FileBizType.GENERAL, false));
        mvc.perform(get("/admin/file/17/download").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void fileDownloadAndPrivatePermissionsCannotDownloadTestData() throws Exception {
        when(storedFiles.get(17L)).thenReturn(file(FileBizType.TEST_DATA, false));
        mvc.perform(get("/admin/file/17/download")
                        .header("Mock-Permissions", "admin:access,file:file:download,file:file:private"))
                .andExpect(jsonPath("$.code").value(403));
        verify(storedFiles, never()).open(any(File.class));
    }

    @Test
    void historicalPublicAvatarDownloadsFromConfinedCopyInsteadOfOldLinuxAbsolutePath() throws Exception {
        File file = file(FileBizType.IMAGE, true);
        file.setStorageProfile(null);
        file.setStorageType(0);
        file.setStoragePath("/home/ymcloud/files/public/image/avatar.jpg");
        file.setUrl("/files/public/image/avatar.jpg");
        when(storedFiles.get(17L)).thenReturn(file);
        byte[] bytes = new byte[]{3, 4, 5};
        when(storedFiles.openLegacyPublic("image/avatar.jpg")).thenReturn(new ByteArrayInputStream(bytes));

        mvc.perform(get("/admin/file/17/download")
                        .header("Mock-Permissions", "admin:access,file:file:download"))
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verify(storedFiles, never()).open(file);
    }

    @Test
    void privateFileNeverUsesHistoricalPublicUrlFallback() throws Exception {
        File file = file(FileBizType.GENERAL, false);
        file.setStorageProfile(null);
        file.setStorageType(0);
        file.setStoragePath("/home/ymcloud/files/private/data.txt");
        file.setUrl("/files/public/image/avatar.jpg");
        when(storedFiles.get(17L)).thenReturn(file);
        when(storedFiles.open(file)).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        mvc.perform(get("/admin/file/17/download")
                        .header("Mock-Permissions", "admin:access,file:file:download,file:file:private"))
                .andExpect(content().bytes(new byte[]{1}));

        verify(storedFiles, never()).openLegacyPublic(any());
    }

    @Test
    void historicalPublicDownloadRejectsTraversalAndEncodedPaths() throws Exception {
        File file = file(FileBizType.IMAGE, true);
        file.setStorageProfile(null);
        file.setStorageType(0);
        when(storedFiles.get(17L)).thenReturn(file);
        for (String path : new String[]{"image/../private.txt", "%2e%2e/secret.txt", "image\\secret.txt", "image/\nsecret.txt"}) {
            file.setUrl("/files/public/" + path);
            mvc.perform(get("/admin/file/17/download")
                            .header("Mock-Permissions", "admin:access,file:file:download"))
                    .andExpect(jsonPath("$.code").value(404));
        }
        verify(storedFiles, never()).openLegacyPublic(any());
        verify(storedFiles, never()).open(file);
    }

    @Test
    void uploadCannotChooseBusinessTypeOrLocationAndPrivateUploadRequiresPermission() throws Exception {
        doAnswer(invocation -> {
            File file = invocation.getArgument(2);
            file.setId(17L);
            file.setStorageProfile("seaweed");
            file.setFileSize(3L);
            return file;
        }).when(storedFiles).save(any(InputStream.class), anyLong(), any(File.class));
        mvc.perform(multipart("/admin/file/upload").file(upload()).param("isPublic", "0")
                        .header("Mock-Permissions", "admin:access,file:file:upload"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(multipart("/admin/file/upload").file(upload()).param("isPublic", "1")
                        .param("bizType", "5").param("bizId", "100").param("objectKey", "chosen")
                        .header("Mock-Permissions", "admin:access,file:file:upload"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bizType").value(0))
                .andExpect(jsonPath("$.data.bizId").doesNotExist())
                .andExpect(jsonPath("$.data.uploaderUsername").value("file-manager"));
        mvc.perform(multipart("/admin/file/upload").file(upload()).param("isPublic", "0")
                        .header("Mock-Permissions", "admin:access,file:file:upload,file:file:private"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isPublic").value(0))
                .andExpect(jsonPath("$.data.url").doesNotExist());
        mvc.perform(multipart("/admin/file/upload").file(upload()).param("isPublic", "7")
                        .header("Mock-Permissions", "admin:access,file:file:upload"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void removePermissionOnlyDeletesUnassociatedGeneralFiles() throws Exception {
        String permissions = "admin:access,file:file:remove,file:file:private";
        for (FileBizType type : new FileBizType[]{FileBizType.IMAGE, FileBizType.PROBLEM_ATTACHMENT,
                FileBizType.CONTEST_ATTACHMENT, FileBizType.ANNOUNCEMENT_ATTACHMENT, FileBizType.TEST_DATA}) {
            when(query.one()).thenReturn(file(type, false));
            mvc.perform(delete("/admin/file/17").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
        }
        File linked = file(FileBizType.GENERAL, false);
        linked.setBizId(10L);
        when(query.one()).thenReturn(linked);
        mvc.perform(delete("/admin/file/17").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verify(storedFiles, never()).delete(anyLong());
        when(query.one()).thenReturn(file(FileBizType.GENERAL, false));
        mvc.perform(delete("/admin/file/17").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        verify(storedFiles).delete(17L);
    }

    @Test
    void superAdministratorHasNewPermissionsButStillCannotDeleteTestData() throws Exception {
        File privateFile = file(FileBizType.GENERAL, false);
        page(List.of(privateFile));
        when(storedFiles.get(17L)).thenReturn(privateFile);
        when(storedFiles.open(privateFile)).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        mvc.perform(get("/admin/file/list").header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].canDownload").value(true))
                .andExpect(jsonPath("$.data.records[0].canDelete").value(true));
        mvc.perform(get("/admin/file/17/download").header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(content().bytes(new byte[]{1}));
        when(query.one()).thenReturn(file(FileBizType.TEST_DATA, false));
        mvc.perform(delete("/admin/file/17").header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(403));
        verify(storedFiles, never()).delete(anyLong());
    }

    @Test
    void paginationRejectsInvalidOrUnboundedRequests() throws Exception {
        for (String[] parameters : new String[][]{{"pageNum", "0"}, {"pageSize", "51"}, {"isPublic", "2"},
                {"bizType", "99"}, {"pageSize", ""}}) {
            mvc.perform(get("/admin/file/list").param(parameters[0], parameters[1])
                            .header("Mock-Permissions", "admin:access,file:file:list"))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @SuppressWarnings("unchecked")
    private void page(List<File> records) {
        doAnswer(invocation -> {
            Page<File> page = invocation.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        }).when(query).page(any(Page.class));
    }

    private File file(FileBizType businessType, boolean publicAccess) {
        File file = new File();
        file.setId(17L);
        file.setFileName("文件.html");
        file.setFileType("text/html");
        file.setStorageProfile("seaweed");
        file.setStoragePath("private/internal-object");
        file.setLegacyPath("C:/internal/file.txt");
        file.setBucket("private-bucket");
        file.setObjectKey("internal-key");
        file.setUrl("/files/public/legacy-url");
        file.setBizType(businessType);
        file.setIsPublic(publicAccess ? 1 : 0);
        return file;
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "new.txt", "text/plain", new byte[]{1, 2, 3});
    }
}
