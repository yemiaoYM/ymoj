package com.ymcloud.web;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import com.ymcloud.web.service.admin.impl.AdminFileServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 使用真实 MyBatis 条件构造器核对分页前的可见性 SQL 和绑定参数
 */
class AdminFileQueryTest {

    private FileEntityService entities;
    private FileMapper mapper;
    private AdminFileServiceImpl service;
    private AnnotationConfigApplicationContext context;
    private final AtomicReference<Wrapper<File>> captured = new AtomicReference<>();
    private final AtomicReference<Page<File>> capturedPage = new AtomicReference<>();

    /** 初始化查询边界 Mock，由 Spring 注入真实文件服务和权限策略。 */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "file-query-test"),
                File.class);
        entities = mock(FileEntityService.class);
        mapper = mock(FileMapper.class);
        when(entities.lambdaQuery()).thenAnswer(invocation -> new LambdaQueryChainWrapper<>(mapper));
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<File> page = invocation.getArgument(0);
            captured.set(invocation.getArgument(1));
            capturedPage.set(page);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });
        context = new AnnotationConfigApplicationContext();
        context.registerBean(FileEntityService.class, () -> entities);
        context.registerBean(StoredFileService.class, () -> mock(StoredFileService.class));
        context.register(PermissionService.class, FileAccessPolicy.class, AdminFileServiceImpl.class);
        context.refresh();
        service = context.getBean(AdminFileServiceImpl.class);
    }

    /** 清理认证状态并关闭隔离的 Spring 上下文。 */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void ordinaryFileListFiltersPrivateAndTestDataBeforePagination() {
        authenticate("file:file:list");
        service.queryList(new AdminFilePageQuery());
        String sql = captured.get().getSqlSegment();
        assertTrue(sql.contains("biz_type <>"));
        assertTrue(sql.contains("OR biz_type IS NULL"));
        assertTrue(sql.contains("is_public ="));
        assertTrue(sql.contains("ORDER BY id DESC"));
        assertTrue(parameters().containsValue(FileBizType.TEST_DATA));
        assertTrue(parameters().containsValue(1));
    }

    @Test
    void testcasePermissionAloneBuildsOnlyTestDataPredicate() {
        authenticate("problem:testcase:query");
        service.queryList(new AdminFilePageQuery());
        String sql = captured.get().getSqlSegment();
        assertTrue(sql.contains("biz_type ="));
        assertFalse(sql.contains("biz_type <>"));
        assertFalse(sql.contains("is_public"));
        assertTrue(parameters().containsValue(FileBizType.TEST_DATA));
    }

    @Test
    void privateFileAdministratorStillExcludesAllTestData() {
        authenticate("file:file:list", "file:file:private", "file:file:download");
        service.queryList(new AdminFilePageQuery());
        String sql = captured.get().getSqlSegment();
        assertTrue(sql.contains("biz_type <>"));
        assertFalse(sql.contains("is_public"));
        assertTrue(parameters().containsValue(FileBizType.TEST_DATA));
    }

    @Test
    void combinedFileAndTestcasePermissionsJoinVisibleGroupsInsteadOfGrantingPrivateAccess() {
        authenticate("file:file:list", "problem:testcase:query");
        service.queryList(new AdminFilePageQuery());
        String sql = captured.get().getSqlSegment();
        assertTrue(sql.contains("is_public ="));
        assertTrue(sql.contains("OR biz_type ="));
        assertTrue(sql.contains("biz_type <>"));
        assertTrue(parameters().containsValue(FileBizType.TEST_DATA));
    }

    @Test
    void testDataIsNeverIncludedByPublicFilterEvenIfLegacyFlagIsPublic() {
        authenticate("file:file:list", "file:file:private", "problem:testcase:query");
        AdminFilePageQuery query = new AdminFilePageQuery();
        query.setIsPublic(1);
        service.queryList(query);
        String sql = captured.get().getSqlSegment();
        assertTrue(sql.contains("is_public ="));
        assertTrue(sql.contains("biz_type <>"));
        assertTrue(parameters().containsValue(FileBizType.TEST_DATA));
    }

    @Test
    void filtersUseBoundValuesAndClientCannotChooseSorting() {
        authenticate("file:file:list", "file:file:private", "problem:testcase:query");
        AdminFilePageQuery query = new AdminFilePageQuery();
        query.setPageNum(3);
        query.setPageSize(50);
        query.setFileName("' OR 1=1 --");
        query.setStorageProfile("seaweed");
        query.setBizType(0);
        service.queryList(query);
        assertFalse(captured.get().getSqlSegment().contains("' OR 1=1 --"));
        assertTrue(parameters().containsValue("%' OR 1=1 --%"));
        assertTrue(parameters().containsValue("seaweed"));
        assertTrue(parameters().containsValue(FileBizType.GENERAL));
        assertTrue(captured.get().getSqlSegment().endsWith("ORDER BY id DESC"));
        assertEquals(3L, capturedPage.get().getCurrent());
        assertEquals(50L, capturedPage.get().getSize());
    }

    @Test
    void noReadPermissionRejectsBeforeAnyDatabaseQuery() {
        authenticate("file:file:private");
        assertThrows(AccessDeniedException.class, () -> service.queryList(new AdminFilePageQuery()));
        assertNull(captured.get());
        verifyNoInteractions(mapper);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parameters() {
        return ((AbstractWrapper<File, ?, ?>) captured.get())
                .getParamNameValuePairs();
    }

    private void authenticate(String... permissions) {
        User user = new User();
        user.setId(9L);
        user.setStatus(UserStatus.OK);
        LoginUser principal = new LoginUser(user, List.of("ADMIN"), Arrays.asList(permissions));
        var authorities = Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
