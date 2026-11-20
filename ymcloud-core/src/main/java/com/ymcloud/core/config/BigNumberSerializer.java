package com.ymcloud.core.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19  16:03
 * @Description: 超出JS最大最小值处理
 */
public class BigNumberSerializer extends JsonSerializer<Long> {

    /**
     * 根据 JS Number.MAX_SAFE_INTEGER 与 Number.MIN_SAFE_INTEGER 得来
     */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    /**
     * JavaScript 最小安全整数。
     */
    private static final long MIN_SAFE_INTEGER = -9007199254740991L;

    /**
     * 序列化数字，超出 JS 安全整数范围时输出字符串。
     *
     * @param value    数字值
     * @param gen      JSON 生成器
     * @param provider 序列化上下文
     */
    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value >= -MAX_SAFE_INTEGER && value <= MAX_SAFE_INTEGER) {
            gen.writeNumber(value);
        } else {
            gen.writeString(value.toString());
        }
    }
}
