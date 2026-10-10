package com.ymcloud.web;

import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.RedisSessionService;
import com.ymcloud.pojo.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证失效连接只重试会话读取，持续故障和并发请求不会无限重连
 */
@ExtendWith(MockitoExtension.class)
public class RedisSessionServiceTest {

    private static final String USER_KEY = "login_tokens:test-session";

    @Mock
    private RedisCache redisCache;

    @Mock
    private LettuceConnectionFactory redisConnectionFactory;

    @InjectMocks
    private RedisSessionService redisSessionService;

    @Test
    void idleConnectionTimeoutRecoversSessionOnNewConnection() {
        LoginUser loginUser = new LoginUser(new User());
        when(redisCache.getCacheObject(USER_KEY))
                .thenThrow(new QueryTimeoutException("Redis command timed out"))
                .thenReturn(loginUser);
        assertSame(loginUser, redisSessionService.getLoginUser(USER_KEY));
        verify(redisConnectionFactory).resetConnection();
        verify(redisCache, times(2)).getCacheObject(USER_KEY);
        verifyNoMoreInteractions(redisCache, redisConnectionFactory);
    }

    @Test
    void connectionFailureAlsoRecoversSession() {
        LoginUser loginUser = new LoginUser(new User());
        when(redisCache.getCacheObject(USER_KEY))
                .thenThrow(new RedisConnectionFailureException("Connection failed"))
                .thenReturn(loginUser);
        assertSame(loginUser, redisSessionService.getLoginUser(USER_KEY));
        verify(redisConnectionFactory).resetConnection();
    }

    @Test
    void persistentTimeoutStopsAfterOneRetry() {
        QueryTimeoutException timeout = new QueryTimeoutException("Redis command timed out");
        when(redisCache.getCacheObject(USER_KEY)).thenThrow(timeout);
        assertSame(timeout, assertThrows(QueryTimeoutException.class,
                () -> redisSessionService.getLoginUser(USER_KEY)));
        verify(redisConnectionFactory).resetConnection();
        verify(redisCache, times(2)).getCacheObject(USER_KEY);
        verifyNoMoreInteractions(redisCache, redisConnectionFactory);
    }

    @Test
    void missingSessionDoesNotReconnectOrExtendExpiry() {
        when(redisCache.getCacheObject(USER_KEY)).thenReturn(null);
        assertNull(redisSessionService.getLoginUser(USER_KEY));
        verify(redisCache).getCacheObject(USER_KEY);
        verifyNoMoreInteractions(redisCache);
        verifyNoInteractions(redisConnectionFactory);
    }

    @Test
    void invalidCachedDataDoesNotReconnect() {
        when(redisCache.getCacheObject(USER_KEY)).thenThrow(new SerializationException("Invalid cached data"));
        assertThrows(SerializationException.class, () -> redisSessionService.getLoginUser(USER_KEY));
        verify(redisCache).getCacheObject(USER_KEY);
        verifyNoMoreInteractions(redisCache);
        verifyNoInteractions(redisConnectionFactory);
    }

    @Test
    void concurrentFailuresReconnectOnceWithoutClosingRecoveredConnection() throws Exception {
        LoginUser loginUser = new LoginUser(new User());
        CountDownLatch firstReads = new CountDownLatch(2);
        AtomicInteger reads = new AtomicInteger();
        when(redisCache.getCacheObject(USER_KEY)).thenAnswer(invocation -> {
            if (reads.incrementAndGet() <= 2) {
                firstReads.countDown();
                assertTrue(firstReads.await(5, TimeUnit.SECONDS));
                throw new QueryTimeoutException("Idle connection timed out");
            }
            return loginUser;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> redisSessionService.getLoginUser(USER_KEY));
            var second = executor.submit(() -> redisSessionService.getLoginUser(USER_KEY));
            assertSame(loginUser, first.get(5, TimeUnit.SECONDS));
            assertSame(loginUser, second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        verify(redisConnectionFactory).resetConnection();
        verify(redisCache, times(4)).getCacheObject(USER_KEY);
    }
}
