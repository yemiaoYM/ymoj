package com.ymcloud.web.judge;

import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** 显式读取 UTF-8 JSON，不接受消息类型头指定任意 Java 类。 */
@Component
public class JudgeResultListener {

    private static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;
    private final JudgeMessageCodec codec;
    private final JudgeResultService results;

    public JudgeResultListener(JudgeMessageCodec codec, JudgeResultService results) {
        this.codec = codec;
        this.results = results;
    }

    @RabbitListener(queues = "${ymcloud.judge.result-queue:" + JudgeProtocol.RESULT_QUEUE + "}",
            containerFactory = "judgeResultListenerContainerFactory")
    public void receive(Message message) {
        if (message.getBody().length > MAX_MESSAGE_BYTES) {
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
            throw new AmqpRejectAndDontRequeueException("评测结果消息无效");
        }
    }
}
