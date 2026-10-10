package com.ymcloud.core.security.service;

import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.domain.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话缓存读取与失效连接恢复
 */
@Service
@Slf4j
public class RedisSessionService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final AtomicLong connectionVersion = new AtomicLong();

    /**
     * 读取会话，连接异常时重建共享连接并重试一次只读操作
     * @param userKey 会话缓存键
     * @return 当前会话，缓存不存在时返回空
     */
    public LoginUser getLoginUser(String userKey) {
        long version = connectionVersion.get();
        try {
            return redisCache.getCacheObject(userKey);
        } catch (QueryTimeoutException | RedisConnectionFailureException e) {
            if (!(redisConnectionFactory instanceof LettuceConnectionFactory factory)) {
                throw e;
            }
            resetConnection(factory, version);
            //仅重试GET，不重放写入、续期或删除等操作
            return redisCache.getCacheObject(userKey);
        }
    }

    /**
     * 同一批失败请求只重建一次共享连接，避免并发重复断开新连接
     * @param factory Lettuce连接工厂
     * @param version 读取会话时的连接版本
     */
    private synchronized void resetConnection(LettuceConnectionFactory factory, long version) {
        if (connectionVersion.get() != version) {
            return;
        }
        log.warn("会话缓存连接异常，重建Redis连接并重试读取");
        factory.resetConnection();
        connectionVersion.incrementAndGet();
    }
}
