package com.ymcloud.web.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 仅解析明确的消息契约，忽略 AMQP 的 Java 类型头，也不启用任意类型反序列化。 */
@Component
public class JudgeMessageCodec {

    private static final int MAX_TASK_MESSAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_RESULT_MESSAGE_BYTES = 16 * 1024 * 1024;
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
            checkLength(payload, message instanceof JudgeTask ? MAX_TASK_MESSAGE_BYTES : MAX_RESULT_MESSAGE_BYTES);
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
        checkLength(payload, type == JudgeTask.class ? MAX_TASK_MESSAGE_BYTES : MAX_RESULT_MESSAGE_BYTES);
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

    /** 与消息消费者采用同一字节上限，计入 UTF-8 多字节字符及 JSON 转义后的实际大小。 */
    private void checkLength(String payload, int maxBytes) {
        if (payload == null || payload.isBlank() || payload.length() > maxBytes
                || payload.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("判题消息为空或超出大小限制");
        }
    }
}
