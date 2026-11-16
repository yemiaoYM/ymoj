package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.config.SecurityConfig;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.filter.AuthenticationEntryPointImpl;
import com.ymcloud.core.security.filter.CustomAccessDeniedHandler;
import com.ymcloud.core.security.filter.JwtAuthenticationTokenFilter;
import com.ymcloud.core.security.filter.LogoutSuccessHandlerImpl;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.controller.admin.common.AdminCarouselController;
import com.ymcloud.web.controller.admin.contest.AdminContestController;
import com.ymcloud.web.controller.admin.problem.AdminProblemController;
import com.ymcloud.web.controller.common.ImageController;
import com.ymcloud.web.controller.oj.PublicFileController;
import com.ymcloud.web.handler.GlobalExceptionHandler;
import com.ymcloud.web.service.admin.AdminContestService;
import com.ymcloud.web.service.admin.AdminProblemService;
import com.ymcloud.web.service.common.CarouselSettingService;
import com.ymcloud.web.service.common.PublicImageService;
import com.ymcloud.web.service.file.FileService;
import com.ymcloud.web.service.file.impl.FileServiceImpl;
import com.ymcloud.web.service.oj.TestDataService;
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
import static org.mockito.Mockito.times;
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
 * @Description: 使用真实安全链及方法鉴权，验证存储代理、迁移和测试数据接口权限边界
 */
class StorageHttpSecurityTest {

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, PublicFileController.class,
            AdminProblemController.class, GlobalExceptionHandler.class, AuthenticationEntryPointImpl.class,
            CustomAccessDeniedHandler.class, LogoutSuccessHandlerImpl.class, ImageController.class,
            AdminCarouselController.class, AdminContestController.class})
    static class TestConfig {

        /**
         * 模拟图片处理边界，鉴权仍通过实际过滤链和方法注解
         * @return 不访问外部存储的图片服务
         */
        @Bean
        PublicImageService publicImageService() {
            return mock(PublicImageService.class);
        }

        /**
         * 隔离轮播配置业务，上传仍使用真实 Controller
         * @return 轮播配置模拟服务
         */
        @Bean
        CarouselSettingService carouselSettingService() {
            return mock(CarouselSettingService.class);
        }

        /**
         * 隔离比赛保存业务，上传仍使用真实 Controller
         * @return 比赛模拟服务
         */
        @Bean
        AdminContestService adminContestService() {
            return mock(AdminContestService.class);
        }

        /**
         * 使用真实公开文件解析规则，其余存储操作由替身隔离
         * @return 保留公开访问校验的文件服务
         */
        @Bean
        FileService storedFileService() {
            FileService service = mock(FileServiceImpl.class);
            org.mockito.Mockito.doCallRealMethod().when(service).resolvePublicFile(any());
            return service;
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
    private FileService files;
    private TestDataService testData;
    private AdminProblemService problems;
    private LambdaQueryChainWrapper<File> publicMetadata;

    /**
     * 装配真实安全过滤链和文件业务服务，模拟存储及数据库边界
     * @throws Exception 测试上下文或模拟请求链初始化失败
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
            beanFactory.registerSingleton("redisCache", mock(RedisCache.class));
            beanFactory.registerSingleton("storageRegistry", mock(com.ymcloud.core.storage.factory.StorageRegistry.class));
            beanFactory.registerSingleton("jwtAuthenticationTokenFilter", jwtFilter);
            beanFactory.registerSingleton("tokenService", mock(TokenService.class));
        });
        context.register(TestConfig.class);
        context.addBeanFactoryPostProcessor(factory -> {
            factory.registerSingleton("problemEntityService", mock(ProblemEntityService.class));
            factory.registerSingleton("contestEntityService", mock(ContestEntityService.class));
        });
        context.refresh();
        files = context.getBean(FileService.class);
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

    /**
     * 公开图片可匿名访问并返回安全响应头
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void publicImageCanBeDownloadedAnonymouslyWithSafeHeaders() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        File file = publicFile("avatar.png", "image/png", bytes.length);
        when(files.resolvePublicUrl("/files/public/6fc6c8cfc3bd4ed989d211bbf350c419")).thenReturn(file);
        when(files.open(file)).thenReturn(new ByteArrayInputStream(bytes));

        mvc.perform(get("/files/public/6fc6c8cfc3bd4ed989d211bbf350c419"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    /**
     * 正文插图共用登录入口，普通用户和题目管理员均可上传，匿名请求不能进入图片处理
     * @throws Exception 请求或断言失败
     */
    @Test
    void markdownUploadRequiresLoginAndReturnsOnlyUrl() throws Exception {
        PublicImageService images = context.getBean(PublicImageService.class);
        mvc.perform(multipart("/markdown/image").file(inputFile()))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(images);
        File image = new File();
        image.setPublicId("c0e28d63621d4830a6492bfa37c8a8c5");
        when(images.upload(any())).thenReturn("/files/public/" + image.getPublicId());
        for (String permissions : List.of("ROLE_USER", "admin:access,problem:problem:add")) {
            mvc.perform(multipart("/markdown/image").file(inputFile()).header("Mock-Permissions", permissions))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("/files/public/" + image.getPublicId()));
        }
        verify(images, times(2)).upload(any());
    }

    /**
     * 各业务的管理员上传入口要求后台准入和对应动作权限，普通账号通配符无效
     * @throws Exception 请求或断言失败
     */
    @Test
    void adminImageUploadsRequireEntryAndActionPermissions() throws Exception {
        PublicImageService images = context.getBean(PublicImageService.class);
        for (String path : List.of("/admin/system-setting/carousel/image", "/admin/contest/image")) {
            mvc.perform(multipart(path).file(inputFile()))
                    .andExpect(jsonPath("$.code").value(401));
            for (String permissions : List.of("ROLE_USER", "admin:access", "admin:access,*:*:*",
                    "system:setting:edit,contest:contest:add,contest:contest:edit")) {
                mvc.perform(multipart(path).file(inputFile()).header("Mock-Permissions", permissions))
                        .andExpect(jsonPath("$.code").value(403));
            }
        }
        mvc.perform(multipart("/admin/system-setting/carousel/image").file(inputFile())
                        .header("Mock-Permissions", "admin:access,contest:contest:add,contest:contest:edit"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(multipart("/admin/contest/image").file(inputFile())
                        .header("Mock-Permissions", "admin:access,system:setting:edit"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(images);
    }

    /**
     * 合法权限按既有地址上传，轮播保留字符串 ID 与 URL，比赛仅返回 URL
     * @throws Exception 请求或断言失败
     */
    @Test
    void adminImageUploadsPreserveResponseContracts() throws Exception {
        PublicImageService images = context.getBean(PublicImageService.class);
        File image = new File();
        image.setId(9007199254740993L);
        image.setPublicId("c0e28d63621d4830a6492bfa37c8a8c5");
        when(images.upload(any())).thenReturn("/files/public/" + image.getPublicId());
        mvc.perform(multipart("/admin/system-setting/carousel/image").file(inputFile())
                        .header("Mock-Permissions", "admin:access,system:setting:edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(image.getPublicId()))
                .andExpect(jsonPath("$.data.url").value("/files/public/" + image.getPublicId()));
        for (String permission : List.of("contest:contest:add", "contest:contest:edit")) {
            mvc.perform(multipart("/admin/contest/image").file(inputFile())
                            .header("Mock-Permissions", "admin:access," + permission))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("/files/public/" + image.getPublicId()));
        }
        verify(images, times(3)).upload(any());
    }

    /**
     * 用户 ID 为 1 的超级管理员仍可使用通配符调用两类管理员上传入口
     * @throws Exception 请求或断言失败
     */
    @Test
    void superAdminCanUploadCarouselAndContestImages() throws Exception {
        PublicImageService images = context.getBean(PublicImageService.class);
        File image = new File();
        image.setId(17L);
        image.setPublicId("c0e28d63621d4830a6492bfa37c8a8c5");
        when(images.upload(any())).thenReturn("/files/public/" + image.getPublicId());
        for (String path : List.of("/admin/system-setting/carousel/image", "/admin/contest/image")) {
            mvc.perform(multipart(path).file(inputFile())
                            .header("Mock-User-Id", "1").header("Mock-Permissions", "*:*:*"))
                    .andExpect(jsonPath("$.code").value(200));
        }
        verify(images, times(2)).upload(any());
    }

    /**
     * 公开 HTML 强制附件下载并禁止内容嗅探
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void publicHtmlIsServedAsAttachmentAndCannotBeSniffedAsActiveContent() throws Exception {
        byte[] bytes = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        File file = publicFile("page.html", "text/html", bytes.length);
        when(files.resolvePublicUrl("/files/public/6fc6c8cfc3bd4ed989d211bbf350c419")).thenReturn(file);
        when(files.open(file)).thenReturn(new ByteArrayInputStream(bytes));

        mvc.perform(get("/files/public/6fc6c8cfc3bd4ed989d211bbf350c419"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
    }

    /**
     * 历史记录没有存储配置时拒绝下载，不按 URL 或绝对路径读取旧目录
     * @throws Exception 请求或断言失败
     */
    @Test
    void historicalPublicUrlWithoutStorageKeyIsRejected() throws Exception {
        File file = publicFile("avatar.jpg", "image/jpeg", 3);
        file.setStorageKey(null);

        when(files.resolvePublicUrl("/files/public/image/avatar.jpg")).thenReturn(file);

        mvc.perform(get("/files/public/image/avatar.jpg"))
                .andExpect(jsonPath("$.code").value(404));
        verify(files, never()).open(any());

    }

    /**
     * 未登记 URL 不能借助公开入口探测或读取本地目录
     * @throws Exception 请求或断言失败
     */
    @Test
    void unregisteredPublicUrlIsRejectedWithoutOpeningStorage() throws Exception {
        mvc.perform(get("/files/public/image/missing.png"))
                .andExpect(jsonPath("$.code").value(404));
        verify(files, never()).open(any());

    }

    /**
     * 空白配置名不能绕过旧目录禁用规则
     * @throws Exception 请求或断言失败
     */
    @Test
    void blankStorageKeyIsRejectedWithoutOpeningStorage() throws Exception {
        File file = publicFile("avatar.png", "image/png", 3);
        file.setStorageKey(" ");
        when(files.resolvePublicUrl("/files/public/image/avatar.png")).thenReturn(file);

        mvc.perform(get("/files/public/image/avatar.png"))
                .andExpect(jsonPath("$.code").value(404));
        verify(files, never()).open(any());

    }

    /**
     * 旧公开地址不能绕过私有记录的访问限制
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void privateRecordWithHistoricalPublicUrlCannotFallBackToLocalBytes() throws Exception {
        when(publicMetadata.exists()).thenReturn(true);

        mvc.perform(get("/files/public/image/private.txt"))
                .andExpect(jsonPath("$.code").value(404));

        verify(files, never()).open(any());
    }

    /**
     * 测试数据即使误标公开也不能从公共入口读取
     * @throws Exception 测试资源操作或请求执行失败
     */
    @Test
    void testDataCannotBeServedPubliclyEvenWhenLegacyRecordIsMarkedPublic() throws Exception {
        File file = publicFile("secret.in", "text/plain", 1);
        file.setBizType(FileBizType.TEST_DATA);
        when(files.resolvePublicUrl("/files/public/6fc6c8cfc3bd4ed989d211bbf350c419")).thenReturn(file);

        mvc.perform(get("/files/public/6fc6c8cfc3bd4ed989d211bbf350c419"))
                .andExpect(jsonPath("$.code").value(404));

        verify(files, never()).open(any(File.class));

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
    void testDataQueryPermissionCannotPublishUploadOrDelete() throws Exception {
        String permissions = "admin:access,problem:testcase:query";
        mvc.perform(get("/admin/problem/10/testcases").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(multipart("/admin/problem/10/testcases/upload").file(inputFile())
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete("/admin/problem/10/testcases")
                        .contentType(MediaType.APPLICATION_JSON).content("[\"1.in\"]")
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verify(problems).getTestCaseFiles(10L);
        verify(problems, never()).uploadTestCaseFile(any(), any());
        verify(problems, never()).deleteTestCaseFiles(any(), any());
    }

    @Test
    void uploadPermissionAllowsPublishButCannotQueryOrDelete() throws Exception {
        String permissions = "admin:access,problem:testcase:add";
        mvc.perform(multipart("/admin/problem/10/testcases/upload").file(inputFile())
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/admin/problem/10/testcases").header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete("/admin/problem/10/testcases")
                        .contentType(MediaType.APPLICATION_JSON).content("[\"1.in\"]")
                        .header("Mock-Permissions", permissions))
                .andExpect(jsonPath("$.code").value(403));
        verify(problems).uploadTestCaseFile(eq(10L), any());
        verify(problems, never()).getTestCaseFiles(any());
        verify(problems, never()).deleteTestCaseFiles(any(), any());
    }

    /**
     * 删除权限不额外授予查询或上传权限
     * @throws Exception 测试资源操作或请求执行失败
     */
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

    /**
     * 构造已绑定存储配置的公开文件元数据
     * @param name 文件名
     * @param contentType MIME 类型
     * @param size 文件字节数
     * @return 可由公开代理读取的文件记录
     */
    private File publicFile(String name, String contentType, long size) {
        File file = new File();
        file.setId(5L);
        file.setIsPublic(1);
        file.setStorageKey("seaweed");
        file.setFileName(name);
        file.setFileType(contentType);
        file.setFileSize(size);
        return file;
    }

    private MockMultipartFile inputFile() {
        return new MockMultipartFile("file", "1.in", "text/plain", "1\n".getBytes(StandardCharsets.UTF_8));
    }
}
