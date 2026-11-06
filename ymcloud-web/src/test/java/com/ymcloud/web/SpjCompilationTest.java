package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.contract.judge.SpjCompileRequest;
import com.ymcloud.pojo.contract.judge.SpjCompileResult;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.SpjCompilation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: SPJ 编译响应身份及按用户、源码隔离的服务端凭据验证
 */
class SpjCompilationTest {

    /**
     * 只有匹配请求的成功结果产生凭据，改变源码或用户不能复用旧凭据。
     * @throws Exception 消息编码失败
     */
    @Test
    void receiptsBelongToExactSourceAndUser() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        RedisCache redis = mock(RedisCache.class);
        SpjCompilation service = new SpjCompilation();
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(service, "codec", new JudgeMessageCodec(mapper));
        ReflectionTestUtils.setField(service, "redis", redis);
        SpjProgram program = new SpjProgram();
        program.setLanguage("C++");
        program.setCode("#include \"testlib.h\"");
        program.setDigest(program.sourceDigest());
        when(rabbit.sendAndReceive(eq(""), eq(SpjProgram.COMPILE_QUEUE), any(Message.class)))
                .thenAnswer(invocation -> {
                    Message message = invocation.getArgument(2);
                    SpjCompileRequest request = mapper.readValue(message.getBody(), SpjCompileRequest.class);
                    return new Message(mapper.writeValueAsBytes(new SpjCompileResult(request.requestId(),
                            request.program().getDigest(), true, "编译通过")));
                });
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(2L);
            assertTrue(service.compile(program).success());
            String key = "judge:spj:compiled:2:" + program.getDigest();
            verify(redis).setCacheObject(key, true, 30, TimeUnit.MINUTES);
            when(redis.getCacheObject(key)).thenReturn(true);
            assertDoesNotThrow(() -> service.requireCompiled(program.getDigest()));
            program.setCode("changed source");
            assertThrows(ProblemException.class, () -> service.requireCompiled(program.sourceDigest()));
            security.when(SecurityUtils::getUserId).thenReturn(3L);
            assertThrows(ProblemException.class, () -> service.requireCompiled(program.getDigest()));
        }
    }

    /**
     * 超时、编译失败及错配响应均不能授予保存凭据。
     * @throws Exception 消息编码失败
     */
    @Test
    void failedOrMismatchedResponsesCannotAuthorizeSaving() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        RedisCache redis = mock(RedisCache.class);
        SpjCompilation service = new SpjCompilation();
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(service, "codec", new JudgeMessageCodec(mapper));
        ReflectionTestUtils.setField(service, "redis", redis);
        SpjProgram program = new SpjProgram();
        program.setLanguage("C++");
        program.setCode("broken source");
        program.setDigest(program.sourceDigest());
        assertThrows(ProblemException.class, () -> service.compile(program));
        when(rabbit.sendAndReceive(eq(""), eq(SpjProgram.COMPILE_QUEUE), any(Message.class)))
                .thenAnswer(invocation -> {
                    SpjCompileRequest request = mapper.readValue(((Message) invocation.getArgument(2)).getBody(), SpjCompileRequest.class);
                    return new Message(mapper.writeValueAsBytes(new SpjCompileResult(request.requestId(),
                            request.program().getDigest(), false, "syntax error")));
                });
        assertFalse(service.compile(program).success());
        when(rabbit.sendAndReceive(eq(""), eq(SpjProgram.COMPILE_QUEUE), any(Message.class)))
                .thenReturn(new Message(mapper.writeValueAsBytes(new SpjCompileResult("other-request",
                        program.getDigest(), true, "ok"))));
        assertThrows(ProblemException.class, () -> service.compile(program));
        verifyNoInteractions(redis);
    }

    /**
     * 编译服务返回非法 JSON 或超限正文时应报告服务异常，不能误报为管理员参数错误
     */
    @Test
    void invalidCompileResponsesAreServiceFailures() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        RedisCache redis = mock(RedisCache.class);
        SpjCompilation service = new SpjCompilation();
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(service, "codec", new JudgeMessageCodec(new ObjectMapper()));
        ReflectionTestUtils.setField(service, "redis", redis);
        SpjProgram program = new SpjProgram();
        program.setLanguage("C++");
        program.setCode("#include \"testlib.h\"");
        program.setDigest(program.sourceDigest());

        List<String> payloads = List.of(
                "",
                "null",
                "private-invalid-response",
                "{\"success\":\"true\"}",
                "{\"success\":true,\"success\":false}",
                "{} {}",
                "{\"message\":\"" + "x".repeat(64 * 1024) + "\"}"
        );
        for (String payload : payloads) {
            when(rabbit.sendAndReceive(eq(""), eq(SpjProgram.COMPILE_QUEUE), any(Message.class)))
                    .thenReturn(new Message(payload.getBytes(StandardCharsets.UTF_8)));
            ProblemException exception = assertThrows(ProblemException.class, () -> service.compile(program));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getCode());
            assertEquals("SPJ 编译响应无效", exception.getMessage());
            assertNull(exception.getCause());
        }
        verifyNoInteractions(redis);
    }
}
