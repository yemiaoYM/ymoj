package com.ymcloud.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.gojudge.GoJudgeExecutor;
import com.ymcloud.judge.gojudge.GoJudgeTaskSession;
import com.ymcloud.judge.listeners.SpjCompileListener;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.SpjCompileRequest;
import com.ymcloud.pojo.contract.judge.SpjCompileResult;
import com.ymcloud.pojo.contract.judge.SpjProgram;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: SPJ 预编译消息入口、诊断隔离与沙箱会话释放验证
 */
class SpjCompileListenerTest {

    /**
     * 合法请求返回同一身份，编译或基础设施失败均关闭会话且不暴露基础设施诊断
     * @throws Exception 消息或模拟编译失败
     */
    @Test
    void compilerRepliesAndAlwaysClosesSession() throws Exception {
        ObjectMapper mapper = new WorkerAmqpConfig().workerObjectMapper();
        GoJudgeExecutor executor = mock(GoJudgeExecutor.class);
        GoJudgeTaskSession session = mock(GoJudgeTaskSession.class);
        SpjCompileListener listener = new SpjCompileListener();
        ReflectionTestUtils.setField(listener, "mapper", mapper);
        ReflectionTestUtils.setField(listener, "executor", executor);
        when(executor.openTask(any(JudgeTask.class))).thenReturn(session);
        SpjProgram program = new SpjProgram();
        program.setLanguage("C++");
        program.setCode("#include \"testlib.h\"");
        program.setDigest(program.sourceDigest());
        String requestId = UUID.randomUUID().toString();
        Message request = new Message(mapper.writeValueAsBytes(new SpjCompileRequest(requestId,
                System.currentTimeMillis() + 30000, program)));
        SpjCompileResult success = mapper.readValue(listener.compile(request).getBody(), SpjCompileResult.class);
        assertTrue(success.success());
        assertEquals(requestId, success.requestId());
        assertEquals(program.getDigest(), success.digest());
        when(executor.compileSpj(eq(session), any(SpjProgram.class)))
                .thenThrow(new CompileException("syntax error"), new IOException("private infrastructure detail"));
        SpjCompileResult syntax = mapper.readValue(listener.compile(request).getBody(), SpjCompileResult.class);
        assertFalse(syntax.success());
        assertEquals("syntax error", syntax.message());
        SpjCompileResult failure = mapper.readValue(listener.compile(request).getBody(), SpjCompileResult.class);
        assertFalse(failure.success());
        assertFalse(failure.message().contains("private infrastructure"));
        verify(session, times(3)).close();
        Message expired = new Message(mapper.writeValueAsBytes(new SpjCompileRequest(requestId, 1, program)));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.compile(expired));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.compile(new Message(new byte[524289])));
        verify(executor, times(3)).openTask(any(JudgeTask.class));
    }
}
