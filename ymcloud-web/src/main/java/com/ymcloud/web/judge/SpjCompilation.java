package com.ymcloud.web.judge;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.contract.judge.SpjCompileRequest;
import com.ymcloud.pojo.contract.judge.SpjCompileResult;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: 经消息队列校验 SPJ 编译并保存按用户与源码版本隔离的通过凭据
 */
@Component
public class SpjCompilation {

    @Autowired
    @Qualifier("judgeRabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JudgeMessageCodec codec;

    @Autowired
    private RedisCache redis;

    /**
     * 请求 Worker 编译，成功后保留三十分钟通过凭据
     * @param program 已从 HTTP 参数构造的源码快照
     * @return 对应请求与源码的编译结果
     */
    public SpjCompileResult compile(SpjProgram program) {
        String requestId = UUID.randomUUID().toString();
        SpjCompileRequest request = new SpjCompileRequest(requestId,
                System.currentTimeMillis() + SpjProgram.COMPILE_TIMEOUT_MILLIS, program);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setExpiration(Long.toString(SpjProgram.COMPILE_TIMEOUT_MILLIS));
        Message response;
        try {
            response = rabbitTemplate.sendAndReceive("", SpjProgram.COMPILE_QUEUE,
                    new Message(codec.write(request).getBytes(StandardCharsets.UTF_8), properties));
        } catch (AmqpException e) {
            throw new ProblemException("SPJ 编译服务暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (response == null) {
            throw new ProblemException("SPJ 编译等待超时，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
        SpjCompileResult result;
        try {
            result = codec.spjResult(new String(response.getBody(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new ProblemException("SPJ 编译响应无效", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!requestId.equals(result.requestId()) || !program.getDigest().equals(result.digest())
                || result.message() == null || result.message().length() > 5000) {
            throw new ProblemException("SPJ 编译响应无效", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (result.success()) {
            redis.setCacheObject(receiptKey(program.getDigest()), true, 30, TimeUnit.MINUTES);
        }
        return result;
    }

    /**
     * 保存新源码前核对服务端通过凭据，不采信浏览器编译状态
     * @param digest 服务端计算的源码摘要
     */
    public void requireCompiled(String digest) {
        if (!Boolean.TRUE.equals(redis.getCacheObject(receiptKey(digest)))) {
            throw new ProblemException("请先编译当前 SPJ 源码，通过后在30分钟内保存", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 隔离不同管理员和不同源码的编译凭据
     * @param digest 源码摘要
     * @return 当前操作者的编译凭据键
     */
    private String receiptKey(String digest) {
        return "judge:spj:compiled:" + SecurityUtils.getUserId() + ":" + digest;
    }
}
