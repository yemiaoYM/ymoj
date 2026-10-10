package com.ymcloud.web;

import com.ymcloud.core.config.RedisConfig;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证连接保活和开发环境故障等待时间，不覆盖其他客户端配置
 */
public class RedisConnectionConfigTest {

    @Test
    void keepAliveRetainsClientSettingsAndProductionTimeout() {
        RedisConfig config = new RedisConfig();
        var builder = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(10))
                .clientOptions(ClientOptions.builder()
                        .requestQueueSize(128)
                        .timeoutOptions(TimeoutOptions.enabled())
                        .build());
        config.lettuceSocketCustomizer().customize(builder);
        LettuceClientConfiguration client = builder.build();
        ClientOptions options = client.getClientOptions().orElseThrow();
        assertEquals(Duration.ofSeconds(10), client.getCommandTimeout());
        assertEquals(128, options.getRequestQueueSize());
        assertTrue(options.getTimeoutOptions().isTimeoutCommands());
        assertTrue(options.getSocketOptions().isKeepAlive());
        assertEquals(Duration.ofSeconds(30), options.getSocketOptions().getKeepAlive().getIdle());
    }

    @Test
    void devTimeoutRemainsBoundedRegardlessOfCustomizerOrder() {
        RedisConfig config = new RedisConfig();
        for (boolean socketFirst : new boolean[]{true, false}) {
            var builder = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(10));
            if (socketFirst) {
                config.lettuceSocketCustomizer().customize(builder);
                config.lettuceDevTimeoutCustomizer().customize(builder);
            } else {
                config.lettuceDevTimeoutCustomizer().customize(builder);
                config.lettuceSocketCustomizer().customize(builder);
            }
            LettuceClientConfiguration client = builder.build();
            ClientOptions options = client.getClientOptions().orElseThrow();
            assertEquals(Duration.ofSeconds(2), client.getCommandTimeout());
            assertEquals(Duration.ofSeconds(2), options.getSocketOptions().getConnectTimeout());
            assertTrue(options.getSocketOptions().isKeepAlive());
            assertEquals(Duration.ofSeconds(30), options.getSocketOptions().getKeepAlive().getIdle());
        }
    }

    @Test
    void devTimeoutDoesNotIncreaseAnAlreadyShorterTimeout() {
        var builder = LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500));
        new RedisConfig().lettuceDevTimeoutCustomizer().customize(builder);
        assertEquals(Duration.ofMillis(500), builder.build().getCommandTimeout());
    }
}
