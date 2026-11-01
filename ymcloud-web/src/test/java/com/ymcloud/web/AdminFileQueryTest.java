package com.ymcloud.web;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import com.ymcloud.web.service.admin.impl.AdminFileServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 校验分页前的服务端授权范围及真实 Mapper XML 的参数绑定，不拼接 SQL
 */
class AdminFileQueryTest {

    private FileEntityService files;
    private AdminFileServiceImpl service;
    private MybatisConfiguration configuration;
    private FileAccessScope scope;
    private Page<File> page;
    private BoundSql bound;

    /**
     * 加载真实权限策略和 Mapper XML，数据库仅捕获分页入口
     * @throws Exception XML 读取失败
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        configuration = new MybatisConfiguration();
        try (var stream = new ClassPathResource("mapper/FileMapper.xml").getInputStream()) {
            new XMLMapperBuilder(stream, configuration, "mapper/FileMapper.xml", configuration.getSqlFragments()).parse();
        }
        files = mock(FileEntityService.class);
        service = new AdminFileServiceImpl();
        ResourceAccessService resources = new ResourceAccessService();
        FileAccessPolicy access = new FileAccessPolicy();
        ReflectionTestUtils.setField(access, "permissions", new PermissionService());
        ReflectionTestUtils.setField(access, "resourceAccess", resources);
        ReflectionTestUtils.setField(service, "resourceAccess", resources);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "files", files);
        when(files.pageAuthorized(any(), any(), any())).thenAnswer(call -> {
            page = call.getArgument(0);
            scope = call.getArgument(2);
            AdminFilePageQuery query = call.getArgument(1);
            bound = configuration.getMappedStatement("com.ymcloud.core.web.mapper.FileMapper.pageAuthorized")
                    .getBoundSql(Map.of("query", query, "scope", scope));
            page.setRecords(List.of());
            return page;
        });
    }

    /**
     * 清理线程认证
     */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 普通文件列表不授予隐藏数据、私有文件或他人题目管理权
     */
    @Test
    void ordinaryListKeepsOwnershipAndPrivateFiltersBeforePagination() {
        authenticate("file:file:list");
        service.queryList(new AdminFilePageQuery());
        assertTrue(scope.isGeneralFiles());
        assertFalse(scope.isPrivateFiles());
        assertFalse(scope.isTestData());
        assertFalse(scope.isAdministrator());
        assertEquals(9L, scope.getUserId());
        assertTrue(bound.getSql().contains("p.author_id = ?"));
        assertTrue(bound.getSql().contains("c.creator_id = ?"));
        assertTrue(bound.getSql().contains("f.is_public = ?"));
        assertEquals(1, bound.getAdditionalParameter("publicAccess"));
        assertEquals(5, bound.getAdditionalParameter("testData"));
    }

    /**
     * 测试数据功能权限只开放本人原题的数据，不开放普通私有文件
     */
    @Test
    void testcasePermissionDoesNotGrantGeneralFiles() {
        authenticate("problem:testcase:query");
        service.queryList(new AdminFilePageQuery());
        assertTrue(scope.isTestData());
        assertTrue(scope.isProblemManager());
        assertFalse(scope.isContestManager());
        assertFalse(scope.isGeneralFiles());
        assertFalse(scope.isPrivateFiles());
    }

    /**
     * 文件私有权限不等价于测试数据权限，两类能力独立
     */
    @Test
    void privateFilePermissionDoesNotGrantTestData() {
        authenticate("file:file:list", "file:file:private");
        service.queryList(new AdminFilePageQuery());
        assertTrue(scope.isPrivateFiles());
        assertFalse(scope.isTestData());
    }

    /**
     * 合并文件与测试点权限时仍保留独立的私有文件限制
     */
    @Test
    void combinedPermissionsKeepPrivateBoundary() {
        authenticate("file:file:list", "problem:testcase:query");
        service.queryList(new AdminFilePageQuery());
        assertTrue(scope.isGeneralFiles());
        assertTrue(scope.isTestData());
        assertFalse(scope.isPrivateFiles());
    }

    /**
     * 历史错误公开标记不能让测试数据进入公开筛选结果
     */
    @Test
    void explicitPublicFilterExcludesTestData() {
        authenticate("file:file:list", "file:file:private", "problem:testcase:query");
        AdminFilePageQuery query = new AdminFilePageQuery();
        query.setIsPublic(1);
        service.queryList(query);
        assertTrue(bound.getSql().contains("AND f.is_public = ? AND (f.biz_type IS NULL OR f.biz_type != ?)"));
    }

    /**
     * 文件名和存储配置只能作为绑定参数，不能改变排序及授权条件
     */
    @Test
    void filtersBindUntrustedValuesAndUseFixedOrdering() {
        authenticate("file:file:list");
        AdminFilePageQuery query = new AdminFilePageQuery();
        query.setPageNum(3);
        query.setPageSize(50);
        query.setFileName("' OR 1=1 --");
        query.setStorageProfile("seaweed");
        query.setBizType(0);
        service.queryList(query);
        assertFalse(bound.getSql().contains(query.getFileName()));
        assertTrue(bound.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("query.fileName")));
        assertTrue(bound.getSql().trim().endsWith("ORDER BY f.id DESC"));
        assertEquals(3, page.getCurrent());
        assertEquals(50, page.getSize());
    }

    /**
     * 缺少查询能力时不执行数据库分页
     */
    @Test
    void noReadPermissionRejectsBeforeQuery() {
        authenticate("file:file:private");
        assertThrows(AccessDeniedException.class, () -> service.queryList(new AdminFilePageQuery()));
        verifyNoInteractions(files);
    }

    /**
     * 装配有指定功能权限的出题人
     * @param permissions 实际权限编码
     */
    private void authenticate(String... permissions) {
        User user = new User();
        user.setId(9L);
        user.setStatus(UserStatus.OK);
        LoginUser principal = new LoginUser(user, List.of("PROBLEM_SETTER"), Arrays.asList(permissions));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
