package com.ymcloud.core.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.ymcloud.common.constant.Constants;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import com.alibaba.fastjson2.filter.Filter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @PackageName: com.ymcloud.core.config
 * @ClassName: FastJson2JsonRedisSerializer
 * @Author: Yemiao
 * @CreateTime: 2025-07-17  21:40
 * @Description: Redis使用FastJson序列化
 */
public class FastJson2JsonRedisSerializer<T> implements RedisSerializer<T> {

    //字符集
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    //严格限制反序列化类型，做好白名单配置
    static final Filter AUTO_TYPE_FILTER = JSONReader.autoTypeFilter(Constants.JSON_WHITELIST_STR);

    private Class<T> clazz;

    public FastJson2JsonRedisSerializer(Class<T> clazz){
        super();
        this.clazz = clazz;
    }

    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        return JSON.toJSONString(t, JSONWriter.Feature.WriteClassName).getBytes(DEFAULT_CHARSET);
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        String str = new String(bytes, DEFAULT_CHARSET);

        return JSON.parseObject(str, clazz, AUTO_TYPE_FILTER);
    }
}
