package com.ymcloud.web.judge;

import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 显式读取 UTF-8 JSON，不接受消息类型头指定任意 Java 类。
 */
@Slf4j
@Component
public class JudgeResultListener {

    private static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;
    @Autowired
    private JudgeMessageCodec codec;

    @Autowired
    private JudgeResultService results;

    /**
     * 校验消息容量及 UTF-8 后调用结果事务，毒消息拒绝且不重新入队。
     * @param message 原始判题结果消息
     */
    @RabbitListener(queues = "${ymcloud.judge.result-queue:" + JudgeProtocol.RESULT_QUEUE + "}",
            containerFactory = "judgeResultListenerContainerFactory")
    public void receive(Message message) {
        if (message.getBody().length > MAX_MESSAGE_BYTES) {
            log.warn("拒绝超出大小限制的判题结果消息，消息大小：{} 字节", message.getBody().length);
            throw new AmqpRejectAndDontRequeueException("评测结果消息超出大小限制");
        }
        try {
            String payload = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(message.getBody()))
                    .toString();
            results.receive(codec.result(payload));
        } catch (CharacterCodingException | IllegalArgumentException e) {
            log.warn("拒绝无效的判题结果消息，消息大小：{} 字节，异常类型：{}",
                    message.getBody().length, e.getClass().getSimpleName());
            throw new AmqpRejectAndDontRequeueException("评测结果消息无效");
        }
    }
}
