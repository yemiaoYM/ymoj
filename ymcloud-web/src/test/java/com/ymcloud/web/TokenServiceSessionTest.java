package com.ymcloud.web;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.Constants;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.RedisSessionService;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.pojo.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 区分无效令牌、已过期会话与Redis暂时不可用
 */
@ExtendWith(MockitoExtension.class)
public class TokenServiceSessionTest {

    private static final String SECRET = "dGVzdC1zZXNzaW9uLXNlY3JldC1mb3ItcmVncmVzc2lvbi10ZXN0cw==";
    private static final String SESSION_ID = "test-session";
    private static final String SESSION_KEY = CacheConstants.LOGIN_TOKEN_KEY + SESSION_ID;

    @Mock
    private RedisCache redisCache;

    @Mock
    private RedisSessionService redisSessionService;

    @InjectMocks
    private TokenService tokenService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(tokenService, "header", "Authorization");
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
    }

    private MockHttpServletRequest request(String sessionId) {
        String token = Jwts.builder()
                .setSubject("test-user")
                .claim(Constants.LOGIN_USER_KEY, sessionId)
                .signWith(SignatureAlgorithm.HS512, SECRET)
                .compact();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/profile");
        request.addHeader("Authorization", Constants.TOKEN_PREFIX + token);
        return request;
    }

    @Test
    void missingTokenDoesNotAccessRedis() {
        assertNull(tokenService.getLoginUser(new MockHttpServletRequest()));
        verifyNoInteractions(redisCache, redisSessionService);
    }

    @Test
    void invalidTokenDoesNotAccessRedis() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "invalid-token");
        assertNull(tokenService.getLoginUser(request));
        verifyNoInteractions(redisCache, redisSessionService);
    }

    @Test
    void missingSessionIdDoesNotAccessRedis() {
        assertNull(tokenService.getLoginUser(request(null)));
        verifyNoInteractions(redisCache, redisSessionService);
    }

    @Test
    void expiredSessionRemainsUnauthenticated() {
        when(redisSessionService.getLoginUser(SESSION_KEY)).thenReturn(null);
        assertNull(tokenService.getLoginUser(request(SESSION_ID)));
    }

    @Test
    void validSessionReturnsCachedUser() {
        LoginUser loginUser = new LoginUser(new User());
        when(redisSessionService.getLoginUser(SESSION_KEY)).thenReturn(loginUser);
        assertSame(loginUser, tokenService.getLoginUser(request(SESSION_ID)));
    }

    @Test
    void redisTimeoutIsNotConvertedToMissingSession() {
        QueryTimeoutException timeout = new QueryTimeoutException("Redis command timed out");
        when(redisSessionService.getLoginUser(SESSION_KEY)).thenThrow(timeout);
        assertSame(timeout, assertThrows(QueryTimeoutException.class,
                () -> tokenService.getLoginUser(request(SESSION_ID))));
        verify(redisCache, never()).deleteObject(anyString());
    }

    @Test
    void redisDisconnectionIsNotConvertedToMissingSession() {
        when(redisSessionService.getLoginUser(SESSION_KEY))
                .thenThrow(new RedisConnectionFailureException("Redis connection failed"));
        assertThrows(RedisConnectionFailureException.class,
                () -> tokenService.getLoginUser(request(SESSION_ID)));
        verify(redisCache, never()).deleteObject(anyString());
    }
}
