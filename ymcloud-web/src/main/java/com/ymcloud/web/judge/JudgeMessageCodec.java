package com.ymcloud.web.judge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * @Author: YeMiaoo
 * @CreateTime: 2026-10-12
 * @Description: 仅解析明确的消息契约，忽略 AMQP 的 Java 类型头，也不启用任意类型反序列化。
 */
@Component
public class JudgeMessageCodec {

    private static final int MAX_TASK_MESSAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_RESULT_MESSAGE_BYTES = 16 * 1024 * 1024;
    private final ObjectMapper objectMapper;

    /**
     * 复用项目 JSON 映射配置，消息解析约束由独立 reader 设置。
     * @param objectMapper 项目 JSON 映射器，不修改其全局解析选项
     */
    public JudgeMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 序列化任务或结果，并按 UTF-8 字节数限制消息大小。
     * @param message JudgeTask 或 JudgeResult，不允许 null
     * @return JSON 消息正文
     * @throws IllegalArgumentException 类型不支持、序列化失败或超过大小限制
     */
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

    /**
     * 解析任务快照，身份和业务约束由调用入口继续校验。
     * @param payload 单份任务 JSON
     * @return 非 null 的任务对象
     * @throws IllegalArgumentException 消息为空、超限或格式非法
     */
    public JudgeTask task(String payload) {
        return read(payload, JudgeTask.class);
    }

    /**
     * 解析结果消息，执行身份和状态转换由结果服务校验。
     * @param payload 单份结果 JSON
     * @return 非 null 的结果对象
     * @throws IllegalArgumentException 消息为空、超限或格式非法
     */
    public JudgeResult result(String payload) {
        return read(payload, JudgeResult.class);
    }

    /**
     * 按固定契约解析；拒绝重复字段和尾随 JSON，不向异常传播私有正文。
     * @param payload JSON 消息正文
     * @param type 调用方指定的任务或结果契约类型
     * @param <T> 消息契约类型
     * @return 非 null 的消息对象
     * @throws IllegalArgumentException 消息超限或格式非法
     */
    private <T> T read(String payload, Class<T> type) {
        checkLength(payload, type == JudgeTask.class ? MAX_TASK_MESSAGE_BYTES : MAX_RESULT_MESSAGE_BYTES);
        try {
            T message = objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readValue(payload);
            if (message == null) {
                throw new IllegalArgumentException("判题消息不得为 null");
            }
            return message;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("判题消息格式无效");
        }
    }

    /**
     * 校验消息大小，计入 UTF-8 多字节字符及 JSON 转义后的实际长度。
     * @param payload 序列化后的 JSON，不允许空白
     * @param maxBytes 当前消息类型允许的最大字节数
     * @throws IllegalArgumentException 消息为空或超过限制
     */
    private void checkLength(String payload, int maxBytes) {
        if (payload == null || payload.isBlank() || payload.length() > maxBytes
                || payload.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("判题消息为空或超出大小限制");
        }
    }
}
