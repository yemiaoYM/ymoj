package com.ymcloud.judge;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.JudgeListener;
import com.ymcloud.judge.listeners.ResultJournal;
import com.ymcloud.judge.listeners.ResultPublisher;
import com.ymcloud.judge.listeners.WorkerExecutionStore;
import com.ymcloud.judge.listeners.WorkerTaskValidator;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 日志提供故障定位信息，同时不泄露源码、测试输入输出及异常中的服务凭据。
 */
class WorkerLoggingTest {

    private static final String PRIVATE_MARKER = "PRIVATE_PAYLOAD_中文_credential_123456";

    private final Logger logger = (Logger) LoggerFactory.getLogger(JudgeListener.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final ObjectMapper mapper = new WorkerAmqpConfig().workerObjectMapper();
    private final WorkerExecutionStore store = mock(WorkerExecutionStore.class);
    private final WorkerExecutionStore.Lease lease = mock(WorkerExecutionStore.Lease.class);
    private final ResultJournal journal = mock(ResultJournal.class);
    private final ResultPublisher publisher = mock(ResultPublisher.class);
    private final JudgeRun runner = mock(JudgeRun.class);
    private final Channel channel = mock(Channel.class);

    private Level originalLevel;
    private JudgeListener listener;
    private JudgeTask task;

    @BeforeEach
    void setup() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        WorkerProperties properties = new WorkerProperties();
        listener = createJudgeListener(mapper, new WorkerTaskValidator(), store, journal,
                publisher, runner, properties, metrics);
        task = WorkerFixtures.task(1, 0);
        task.setCode("// " + PRIVATE_MARKER + "\nclass Main {}");
        task.setInput(PRIVATE_MARKER);
        task.setExpectedOutput(PRIVATE_MARKER);
    }

    @AfterEach
    void close() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        appender.stop();
        metrics.close();
    }

    @Test
    void malformedMessageDoesNotLogRawBodyOrParserDiagnostic() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        listener.listenJudge(new Message(("{\"code\":\"" + PRIVATE_MARKER)
                .getBytes(StandardCharsets.UTF_8), properties), channel);

        verify(channel).basicReject(7L, false);
        assertTrue(logs().contains("无效"));
        assertPrivateLogs();
    }

    @Test
    void successfulExecutionLogsIdentityAndVerdictWithoutTaskOrResultBody() throws Exception {
        JudgeResult result = result();
        TestCaseResult testCase = new TestCaseResult();
        testCase.setCaseIndex(1);
        testCase.setStatus(JudgeVerdict.STATUS_ACCEPTED);
        testCase.setOutput(PRIVATE_MARKER);
        testCase.setErrorMessage(PRIVATE_MARKER);
        result.setTestCaseResults(List.of(testCase));
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(lease, null, false, "digest"));
        when(lease.attempt()).thenReturn(1);
        when(runner.execute(any(), any())).thenReturn(result);

        listener.listenJudge(message(), channel);

        verify(channel).basicAck(7L, false);
        assertTrue(logs().contains(task.getExecutionId()));
        assertTrue(logs().contains("STATUS_ACCEPTED"));
        assertPrivateLogs();
    }

    @Test
    void executionAndRetryFailureLogsExceptionTypesWithoutExceptionMessages() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(lease, null, false, "digest"));
        when(lease.attempt()).thenReturn(1);
        when(runner.execute(any(), any())).thenThrow(new IOException(PRIVATE_MARKER));
        doThrow(new IllegalStateException(PRIVATE_MARKER)).when(publisher).retry(task);

        listener.listenJudge(message(), channel);

        verify(channel).basicReject(7L, false);
        assertTrue(logs().contains(task.getExecutionId()));
        assertTrue(logs().contains("IOException"));
        assertTrue(logs().contains("IllegalStateException"));
        assertPrivateLogs();
    }

    private JudgeResult result() {
        return JudgeResult.builder()
                .executionId(task.getExecutionId())
                .submissionId(task.getSubmissionId())
                .strategy(task.getStrategy())
                .terminal(true)
                .sequence(3)
                .status(JudgeVerdict.STATUS_ACCEPTED)
                .build();
    }

    private Message message() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        return new Message(mapper.writeValueAsBytes(task), properties);
    }

    private String logs() {
        return String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    private void assertPrivateLogs() {
        assertFalse(appender.list.isEmpty());
        assertFalse(logs().contains(PRIVATE_MARKER));
        for (ILoggingEvent event : appender.list) {
            assertNull(event.getThrowableProxy(), "异常堆栈中的 message 可能包含消息正文或凭据");
        }
    }

    /**
     * 装配字段注入组件，网络和存储依赖由本测试显式提供。
     * @param mapper 判题专用 JSON 映射器
     * @param validator 真实任务入口校验器
     * @param store 可控制租约状态的执行存储
     * @param journal 本地终态恢复边界
     * @param publisher 可控制确认结果的消息发布器
     * @param runner 当前任务的执行入口
     * @param properties 本测试使用的容量、截止或发布配置
     * @param metrics 隔离的测试指标注册表
     * @return 已注入依赖、尚未主动执行生命周期回调的测试组件
     */
    private static JudgeListener createJudgeListener(
            ObjectMapper mapper,
            WorkerTaskValidator validator,
            WorkerExecutionStore store,
            ResultJournal journal,
            ResultPublisher publisher,
            JudgeRun runner,
            WorkerProperties properties,
            MeterRegistry metrics) {
        JudgeListener component = new JudgeListener();
        ReflectionTestUtils.setField(component, "mapper", mapper);
        ReflectionTestUtils.setField(component, "validator", validator);
        ReflectionTestUtils.setField(component, "store", store);
        ReflectionTestUtils.setField(component, "journal", journal);
        ReflectionTestUtils.setField(component, "publisher", publisher);
        ReflectionTestUtils.setField(component, "runner", runner);
        ReflectionTestUtils.setField(component, "properties", properties);
        ReflectionTestUtils.setField(component, "metrics", metrics);
        return component;
    }
}
