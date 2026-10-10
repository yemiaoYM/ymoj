package com.ymcloud.core.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * @PackageName: com.ymcloud.core.config
 * @ClassName: RedisConfig
 * @Author: Yemiao
 * @CreateTime: 2025-07-17  21:28
 * @Description: Redis配置文件
 */
@Configuration
@Slf4j
public class RedisConfig {

    /**
     * Redis连接保活，及时发现空闲连接失效
     * @return Lettuce客户端配置
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceSocketCustomizer() {
        return builder -> {
            ClientOptions options = builder.build().getClientOptions().orElseGet(ClientOptions::create);
            SocketOptions socketOptions = options.getSocketOptions().mutate()
                    .keepAlive(SocketOptions.KeepAliveOptions.builder()
                            .enable()
                            .idle(Duration.ofSeconds(30))
                            .interval(Duration.ofSeconds(10))
                            .count(3)
                            .build())
                    .build();
            builder.clientOptions(options.mutate()
                    .socketOptions(socketOptions)
                    .build());
        };
    }

    /**
     * 开发环境快速返回连接故障，避免超过前端请求等待时间
     * @return 开发环境连接超时配置
     */
    @Bean
    @Profile("dev")
    public LettuceClientConfigurationBuilderCustomizer lettuceDevTimeoutCustomizer() {
        return builder -> {
            ClientOptions options = builder.build().getClientOptions().orElseGet(ClientOptions::create);
            Duration timeout = Duration.ofSeconds(2);
            if (builder.build().getCommandTimeout().compareTo(timeout) > 0) {
                builder.commandTimeout(timeout);
            }
            SocketOptions socketOptions = options.getSocketOptions();
            if (socketOptions.getConnectTimeout().compareTo(timeout) > 0) {
                builder.clientOptions(options.mutate()
                        .socketOptions(socketOptions.mutate()
                                .keepAlive(socketOptions.getKeepAlive())
                                .connectTimeout(timeout)
                                .build())
                        .build());
            }
        };
    }

    /**
     * redis配置
     */
    @Bean
    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory factory) {
        log.info("开始创建redis模板对象...");
        //<String, Object>是为了项目方便而设置
        //创建 RedisTemplate实例
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        //使用 StringRedisSerializer 序列化 key 和 hashKey
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        //使用官方序列化
        //使用 GenericJackson2JsonRedisSerializer 序列化 value 和 hashValue
        //GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        //使用自定义序列化
        //使用 FastJson2JsonRedisSerializer 序列化 value 和 hashValue
        //严格限制反序列化类型，做好白名单配置 性能高
        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        //初始化模板
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 基于注释的redis缓存配置
     */
    @Bean
    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        log.info("开始创建RedisCacheManager...");
        //key序列化器
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        //value序列化器,使用自定义序列化
        FastJson2JsonRedisSerializer valueSerializer = new FastJson2JsonRedisSerializer(Object.class);
        //配置默认缓存配置
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                //修改默认的：：为：，统一redis缓存命名
                .computePrefixWith(cacheName -> cacheName + ":");
        //返回配置
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

}

