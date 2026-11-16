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
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.common.AdminFileController;
import com.ymcloud.web.handler.GlobalExceptionHandler;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import com.ymcloud.web.service.admin.impl.AdminFileServiceImpl;
import com.ymcloud.web.service.file.FileService;
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
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 通过真实安全链验证文件权限和资源归属
 */
class AdminFileHttpSecurityTest {

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AdminFileController.class, AdminFileServiceImpl.class, FileAccessPolicy.class,
            ResourceAccessService.class, GlobalExceptionHandler.class, AuthenticationEntryPointImpl.class, CustomAccessDeniedHandler.class,
            LogoutSuccessHandlerImpl.class})
    static class TestConfig {

        /**
         * 隔离题目、比赛的资源归属查询
         * @return 测试替身
         */
        @Bean
        ProblemEntityService problemEntityService() {
            return mock(ProblemEntityService.class);
        }

        /**
         * 隔离题目、比赛的资源归属查询
         * @return 测试替身
         */
        @Bean
        ContestEntityService contestEntityService() {
            return mock(ContestEntityService.class);
        }

        /**
         * 隔离题目、比赛的资源归属查询
         * @return 测试替身
         */
        @Bean
        ContestUserEntityService contestUserEntityService() {
            return mock(ContestUserEntityService.class);
        }

        /**
         * 构建文件服务替身，隔离 HTTP 权限测试与对象存储
         * @return 文件服务替身
         */
        @Bean
        FileService storedFileService() {
            return mock(FileService.class);
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
    private FileService storedFiles;
    private FileEntityService entities;
    private LambdaQueryChainWrapper<File> query;

    /**
     * 装配真实安全链及文件业务服务，用请求头控制隔离认证身份
     * @throws Exception 测试请求过滤器装配失败
     */
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
        storedFiles = context.getBean(FileService.class);
        Problem owned = new Problem();
        owned.setId(10L);
        owned.setAuthorId(9L);
        when(context.getBean(ProblemEntityService.class).getById(10L)).thenReturn(owned);
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

    /**
     * 匿名请求在业务查询和存储访问前被拒绝
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void anonymousRequestsAreRejectedBeforeBusinessOrStorageAccess() throws Exception {
        mvc.perform(get("/admin/file/list")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/admin/file/b2b4724a5da148fa9437b46b52d174c8/download")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(multipart("/admin/file/upload").file(upload()).param("isPublic", "1"))
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(delete("/admin/file/b2b4724a5da148fa9437b46b52d174c8")).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(storedFiles);
        verify(entities, never()).lambdaQuery();
    }

    /**
     * 后台入口与操作权限不能由角色名称或普通通配符替代
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void adminEntryAndEndpointPermissionCannotBeSubstitutedByRoleOrWildcard() throws Exception {
        mvc.perform(get("/admin/file/list").header("Mock-Permissions", "file:file:list"))
                .andExpect(jsonPath("$.code").value(403));
        for (String permissions : new String[]{"admin:access", "admin:access,ROLE_ADMIN,*:*:*,file:file:*"}) {
            mvc.perform(get("/admin/file/list").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
            mvc.perform(get("/admin/file/b2b4724a5da148fa9437b46b52d174c8/download").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
            mvc.perform(delete("/admin/file/b2b4724a5da148fa9437b46b52d174c8").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
        }
        verifyNoInteractions(storedFiles);
    }

    /**
     * 列表只暴露允许的元数据，列表权限不授予下载和删除
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void fileListExposesOnlySafeMetadataAndDoesNotGrantDownloadOrDeletion() throws Exception {
        page(List.of(file(FileBizType.GENERAL, true)));
        mvc.perform(get("/admin/file/list").header("Mock-Permissions", "admin:access,file:file:list"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].bizType").value(0))
                .andExpect(jsonPath("$.data.records[0].storageKey").value("seaweed"))
                .andExpect(jsonPath("$.data.records[0].canDownload").value(false))
                .andExpect(jsonPath("$.data.records[0].canDelete").value(false))
                .andExpect(jsonPath("$.data.records[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].legacyPath").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].bucket").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].objectKey").doesNotExist());
    }

    /**
     * 私有文件查询和下载同时要求对应私有权限
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void privateMetadataAndDownloadsNeedSpecificPrivatePermission() throws Exception {
        File file = file(FileBizType.GENERAL, false);
        page(List.of());
        when(storedFiles.getByPublicId("b2b4724a5da148fa9437b46b52d174c8")).thenReturn(file);
        mvc.perform(get("/admin/file/list").header("Mock-Permissions", "admin:access,file:file:list"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(get("/admin/file/b2b4724a5da148fa9437b46b52d174c8/download").header("Mock-Permissions", "admin:access,file:file:download"))
                .andExpect(jsonPath("$.code").value(403));
        page(List.of(file));
        mvc.perform(get("/admin/file/list")
                        .header("Mock-Permissions", "admin:access,file:file:list,file:file:private"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].url").doesNotExist());
        verify(storedFiles, never()).open(any(File.class));
    }

    /**
     * 测试数据管理权限不授予普通私有文件访问
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void testcaseManagerCanReadTestDataButCannotReadOrdinaryPrivateFiles() throws Exception {
        File testData = file(FileBizType.TEST_DATA, true);
        page(List.of(testData));
        when(storedFiles.getByPublicId("b2b4724a5da148fa9437b46b52d174c8")).thenReturn(testData);
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
        mvc.perform(get("/admin/file/b2b4724a5da148fa9437b46b52d174c8/download").header("Mock-Permissions", permissions))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
        when(storedFiles.getByPublicId("b2b4724a5da148fa9437b46b52d174c8")).thenReturn(file(FileBizType.GENERAL, false));
        mvc.perform(get("/admin/file/b2b4724a5da148fa9437b46b52d174c8/download").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
    }

    /**
     * 普通下载及私有权限不能读取隐藏测试数据
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void fileDownloadAndPrivatePermissionsCannotDownloadTestData() throws Exception {
        when(storedFiles.getByPublicId("b2b4724a5da148fa9437b46b52d174c8")).thenReturn(file(FileBizType.TEST_DATA, false));
        mvc.perform(get("/admin/file/b2b4724a5da148fa9437b46b52d174c8/download")
                        .header("Mock-Permissions", "admin:access,file:file:download,file:file:private"))
                .andExpect(jsonPath("$.code").value(403));
        verify(storedFiles, never()).open(any(File.class));
    }

    /**
     * 上传不能指定业务类型和物理位置，私有上传另行鉴权
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void uploadCannotChooseBusinessTypeOrLocationAndPrivateUploadRequiresPermission() throws Exception {
        doAnswer(invocation -> {
            File file = StoredFileFixture.saved(invocation.getArgument(0));
            file.setId(17L);
            file.setStorageKey("seaweed");
            file.setFileSize(3L);
            return file;
        }).when(storedFiles).create(any(CreateStoredFileCommand.class), any(InputStream.class), anyLong());
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

    /**
     * 删除权限只能删除尚未关联的普通文件
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void removePermissionOnlyDeletesUnassociatedGeneralFiles() throws Exception {
        String permissions = "admin:access,file:file:remove,file:file:private";
        for (FileBizType type : new FileBizType[]{FileBizType.IMAGE, FileBizType.PROBLEM_ATTACHMENT,
                FileBizType.CONTEST_ATTACHMENT, FileBizType.ANNOUNCEMENT_ATTACHMENT, FileBizType.TEST_DATA}) {
            when(query.one()).thenReturn(file(type, false));
            mvc.perform(delete("/admin/file/b2b4724a5da148fa9437b46b52d174c8").header("Mock-Permissions", permissions))
                    .andExpect(jsonPath("$.code").value(403));
        }
        File linked = file(FileBizType.GENERAL, false);
        linked.setBizId(10L);
        when(query.one()).thenReturn(linked);
        mvc.perform(delete("/admin/file/b2b4724a5da148fa9437b46b52d174c8").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verify(storedFiles, never()).delete(anyLong());
        when(query.one()).thenReturn(file(FileBizType.GENERAL, false));
        mvc.perform(delete("/admin/file/b2b4724a5da148fa9437b46b52d174c8").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        verify(storedFiles).delete(17L);
    }

    /**
     * 超级管理员具备新权限但通用入口仍禁止删除测试数据
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void superAdministratorHasNewPermissionsButStillCannotDeleteTestData() throws Exception {
        File privateFile = file(FileBizType.GENERAL, false);
        page(List.of(privateFile));
        when(storedFiles.getByPublicId("b2b4724a5da148fa9437b46b52d174c8")).thenReturn(privateFile);
        when(storedFiles.open(privateFile)).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        mvc.perform(get("/admin/file/list").header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].canDownload").value(true))
                .andExpect(jsonPath("$.data.records[0].canDelete").value(true));
        mvc.perform(get("/admin/file/b2b4724a5da148fa9437b46b52d174c8/download").header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                .andExpect(content().bytes(new byte[]{1}));
        when(query.one()).thenReturn(file(FileBizType.TEST_DATA, false));
        mvc.perform(delete("/admin/file/b2b4724a5da148fa9437b46b52d174c8").header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
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

    /**
     * 设置授权过滤后的文件分页结果
     * @param records 当前页可见文件
     */
    @SuppressWarnings("unchecked")
    private void page(List<File> records) {
        doAnswer(invocation -> {
            Page<File> page = invocation.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        }).when(entities).pageAuthorized(any(Page.class), any(), any());
    }

    /**
     * 构建具有规范对象位置的权限测试文件
     * @param businessType 文件业务类型
     * @param publicAccess 是否允许公开访问
     * @return 文件元数据夹具
     */
    private File file(FileBizType businessType, boolean publicAccess) {
        File file = new File();
        file.setId(17L);
        file.setFileName("文件.html");
        file.setFileType("text/html");
        file.setStorageKey("seaweed");

        file.setBucket("private-bucket");
        file.setObjectKey("internal-key");
        file.setPublicId("b2b4724a5da148fa9437b46b52d174c8");
        file.setBizType(businessType);
        file.setBizId(businessType == FileBizType.GENERAL ? null : 10L);
        file.setIsPublic(publicAccess ? 1 : 0);
        return file;
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "new.txt", "text/plain", new byte[]{1, 2, 3});
    }
}
