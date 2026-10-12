package com.ymcloud.web.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import org.springframework.stereotype.Component;

/** 仅解析明确的消息契约，忽略 AMQP 的 Java 类型头，也不启用任意类型反序列化。 */
@Component
public class JudgeMessageCodec {

    private static final int MAX_PAYLOAD_LENGTH = 16 * 1024 * 1024;
    private final ObjectMapper objectMapper;

    public JudgeMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object message) {
        if (!(message instanceof JudgeTask) && !(message instanceof JudgeResult)) {
            throw new IllegalArgumentException("判题消息类型无效");
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            checkLength(payload);
            return payload;
        } catch (JsonProcessingException e) {
            // Jackson 的异常可能携带私有代码或结果正文，不向消息框架传递原始异常。
            throw new IllegalArgumentException("判题消息序列化失败");
        }
    }

    public JudgeTask task(String payload) {
        return read(payload, JudgeTask.class);
    }

    public JudgeResult result(String payload) {
        return read(payload, JudgeResult.class);
    }

    private <T> T read(String payload, Class<T> type) {
        checkLength(payload);
        try {
            T message = objectMapper.readValue(payload, type);
            if (message == null) {
                throw new IllegalArgumentException("判题消息不得为 null");
            }
            return message;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("判题消息格式无效");
        }
    }

    private void checkLength(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("判题消息为空或超出大小限制");
        }
    }
}
