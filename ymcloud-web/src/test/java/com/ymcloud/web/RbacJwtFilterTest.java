package com.ymcloud.web;

import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.filter.JwtAuthenticationTokenFilter;
import com.ymcloud.core.security.service.AuthorizationService;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.pojo.entity.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证真实JWT过滤链使用当前权限而不是旧缓存
 */
@ExtendWith(MockitoExtension.class)
public class RbacJwtFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private JwtAuthenticationTokenFilter filter;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private LoginUser loginUser() {
        User user = new User();
        user.setId(9L);
        user.setUsername("editor");
        LoginUser login = new LoginUser(user, List.of("EDITOR"), List.of("admin:access", "system:role:auth"));
        login.setToken("mock-token");
        return login;
    }

    @Test
    void filterAuthenticatesWithRefreshedAuthorities() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginUser login = loginUser();
        when(tokenService.getLoginUser(request)).thenReturn(login);
        when(authorizationService.refreshAuthorization(login)).thenAnswer(invocation -> {
            login.setPermissions(List.of("admin:access"));
            return true;
        });
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication);
            assertFalse(authentication.getAuthorities().stream().anyMatch(a -> "system:role:auth".equals(a.getAuthority())));
        });
        verify(tokenService).verifyToken(login);
    }
    @Test
    void revokedAccountInvalidatesTokenAndDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginUser login = loginUser();
        when(tokenService.getLoginUser(request)).thenReturn(login);
        when(authorizationService.refreshAuthorization(login)).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenService).delLoginUser("mock-token");
        verify(tokenService, never()).verifyToken(any());
        verify(chain).doFilter(any(), any());
    }
}
