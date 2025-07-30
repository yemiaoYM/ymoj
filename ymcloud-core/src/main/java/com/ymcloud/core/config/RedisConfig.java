package com.ymcloud.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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

    @Bean
    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory factory, ObjectMapper redisObjectMapper) {
        log.info("开始创建redis模板对象...");
        //<String, Object>是为了项目方便而设置
        //创建 RedisTemplate实例
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        //使用 StringRedisSerializer 序列化 key 和 hashKey
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        //使用 GenericJackson2JsonRedisSerializer 序列化 value 和 hashValue
        //GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        //使用 FastJson2JsonRedisSerializer 序列化 value 和 hashValue
        //严格限制反序列化类型，做好白名单配置 性能高
        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        //初始化模板
        template.afterPropertiesSet();
        return template;
    }
}

