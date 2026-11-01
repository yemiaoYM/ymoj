package com.ymcloud.web;

import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 实际权限匹配拒绝普通账号通配符，文件功能权限不替代原题归属
 */
class FileAccessPolicyTest {

    private final FileAccessPolicy access = new FileAccessPolicy();

    /**
     * 装配真实权限策略及可控制的资源归属
     */
    @BeforeEach
    void setup() {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        ContestEntityService contests = mock(ContestEntityService.class);
        Problem problem = new Problem();
        problem.setId(10L);
        problem.setAuthorId(9L);
        Contest contest = new Contest();
        contest.setId(10L);
        contest.setCreatorId(9L);
        when(problems.getById(10L)).thenReturn(problem);
        when(contests.getById(10L)).thenReturn(contest);
        ReflectionTestUtils.setField(access, "permissions", new PermissionService());
        ReflectionTestUtils.setField(access, "resourceAccess", new ResourceAccessService());
        ReflectionTestUtils.setField(access, "problems", problems);
        ReflectionTestUtils.setField(access, "contests", contests);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousAndOrdinaryWildcardHaveNoFileAccess() {
        assertFalse(access.canList(file(FileBizType.GENERAL, true)));
        assertFalse(access.canDownload(file(FileBizType.TEST_DATA, false)));
        authenticate(9L, "*:*:*", "file:file:*", "problem:testcase:*");
        assertFalse(access.canList(file(FileBizType.GENERAL, true)));
        assertFalse(access.canDownload(file(FileBizType.TEST_DATA, false)));
        assertFalse(access.canDelete(file(FileBizType.GENERAL, true)));
        assertThrows(AccessDeniedException.class, () -> access.requireUpload(true));
    }

    @Test
    void testcaseManagerCannotAccessOrdinaryPrivateOrPublicFiles() {
        authenticate(9L, "problem:testcase:query");
        assertTrue(access.canList(file(FileBizType.TEST_DATA, false)));
        assertTrue(access.canDownload(file(FileBizType.TEST_DATA, false)));
        assertFalse(access.canList(file(FileBizType.GENERAL, false)));
        assertFalse(access.canDownload(file(FileBizType.GENERAL, true)));
        assertFalse(access.canDelete(file(FileBizType.TEST_DATA, false)));
    }

    @Test
    void fileAdministratorNeedsPrivatePermissionAndCannotSubstituteTestcasePermission() {
        authenticate(9L, "file:file:list", "file:file:download", "file:file:remove");
        assertTrue(access.canList(file(FileBizType.GENERAL, true)));
        assertTrue(access.canDownload(file(FileBizType.GENERAL, true)));
        assertFalse(access.canList(file(FileBizType.GENERAL, false)));
        assertFalse(access.canDownload(file(FileBizType.GENERAL, false)));
        assertFalse(access.canDelete(file(FileBizType.GENERAL, false)));

        authenticate(9L, "file:file:list", "file:file:download", "file:file:remove", "file:file:private");
        assertTrue(access.canList(file(FileBizType.GENERAL, false)));
        assertTrue(access.canDownload(file(FileBizType.GENERAL, false)));
        assertTrue(access.canDelete(file(FileBizType.GENERAL, false)));
        assertFalse(access.canList(file(FileBizType.TEST_DATA, false)));
        assertFalse(access.canDownload(file(FileBizType.TEST_DATA, false)));
    }

    @Test
    void privateUploadRequiresBothUploadAndPrivatePermissions() {
        authenticate(9L, "file:file:upload");
        access.requireUpload(true);
        assertThrows(AccessDeniedException.class, () -> access.requireUpload(false));
        authenticate(9L, "file:file:private");
        assertThrows(AccessDeniedException.class, () -> access.requireUpload(false));
        authenticate(9L, "file:file:upload", "file:file:private");
        access.requireUpload(false);
    }

    @ParameterizedTest
    @EnumSource(FileBizType.class)
    void superAdministratorStillCannotDeleteBusinessOrVersionFiles(FileBizType businessType) {
        authenticate(1L, "*:*:*");
        File file = file(businessType, false);
        assertTrue(access.canList(file));
        assertTrue(access.canDownload(file));
        if (businessType == FileBizType.GENERAL) {
            assertTrue(access.canDelete(file));
            file.setBizId(0L);
            assertTrue(access.canDelete(file));
            file.setBizId(10L);
        }
        assertFalse(access.canDelete(file));
    }

    @Test
    void testDataIsPrivateEvenWhenCorruptLegacyMetadataSaysPublic() {
        authenticate(9L, "file:file:list", "file:file:download", "file:file:private");
        File file = file(FileBizType.TEST_DATA, true);
        assertFalse(access.isPublic(file));
        assertFalse(access.canList(file));
        assertFalse(access.canDownload(file));
        authenticate(9L, "problem:testcase:query");
        assertTrue(access.canList(file));
        assertTrue(access.canDownload(file));
        assertFalse(access.isPublic(file));
    }

    private File file(FileBizType businessType, boolean publicAccess) {
        File file = new File();
        file.setBizType(businessType);
        file.setIsPublic(publicAccess ? 1 : 0);
        if (businessType != FileBizType.GENERAL) {
            file.setBizId(10L);
        }
        return file;
    }

    private void authenticate(Long id, String... permissions) {
        User user = new User();
        user.setId(id);
        user.setStatus(UserStatus.OK);
        LoginUser principal = new LoginUser(user, List.of("PROBLEM_SETTER"), Arrays.asList(permissions));
        var authorities = Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
