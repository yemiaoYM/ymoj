package com.ymcloud.judge;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.RabbitMQConstants;
import com.ymcloud.common.exception.judge.CompileException;
import com.ymcloud.common.exception.judge.SandBoxException;
import com.ymcloud.common.utils.MessageQueueUtils;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.judge.bo.CodeRunResult;
import com.ymcloud.judge.bo.JudgeResult;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import com.ymcloud.judge.sandbox.SandBox;
import com.ymcloud.judge.sandbox.SandBoxFactory;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.bo.JudgeTask;
import com.ymcloud.pojo.bo.TestDataManifest;
import com.ymcloud.pojo.entity.Language;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 不启动 Docker：验证固定版本、逐点读取、输入空白及系统错误分类。
 */
@ExtendWith(MockitoExtension.class)
class JudgeRunVersionedDataTest {

    @Mock
    private DockerClient dockerClient;
    @Mock
    private RedisCache redisCache;
    @Mock
    private MessageQueueUtils messageQueueUtils;
    @Mock
    private TestDataService testDataService;
    @Mock
    private CodeExecContainer container;
    @Mock
    private SandBox sandBox;
    @InjectMocks
    private JudgeRun judgeRun;
    @TempDir
    private Path workingDir;

    @BeforeEach
    void configureWorkingDirectories() {
        ReflectionTestUtils.setField(judgeRun, "hostWorkingDir", workingDir.toString());
        ReflectionTestUtils.setField(judgeRun, "containerWorkingDir", "/workspace");
    }

    @Test
    void queuedVersionIsUsedAndPayloadsAreReadOnlyWhenTheirCaseRuns() {
        JudgeTask task = task(JudgeConstants.DEFAULT_JUDGE, JudgeConstants.ACM);
        when(testDataService.getManifest(10L, 77L)).thenReturn(manifest(77L, 2));
        when(testDataService.openFile(10L, 77L, "/in/1.in")).thenReturn(stream("  first\n\n"));
        when(testDataService.openFile(10L, 77L, "/out/1.out")).thenReturn(stream("answer  \r\n\r\n"));
        when(testDataService.openFile(10L, 77L, "/in/2.in")).thenReturn(stream("second"));
        when(testDataService.openFile(10L, 77L, "/out/2.out")).thenReturn(stream("answer\n"));
        when(sandBox.execute(eq(container), anyString(), eq(1200L)))
                .thenReturn(new CodeRunResult("answer\n", null, 3L, 20L));

        runWithSandbox(task);

        var order = inOrder(testDataService, sandBox);
        order.verify(testDataService).getManifest(10L, 77L);
        order.verify(testDataService).openFile(10L, 77L, "/in/1.in");
        order.verify(testDataService).openFile(10L, 77L, "/out/1.out");
        order.verify(sandBox).execute(container, "  first\n\n", 1200L);
        order.verify(testDataService).openFile(10L, 77L, "/in/2.in");
        order.verify(testDataService).openFile(10L, 77L, "/out/2.out");
        order.verify(sandBox).execute(container, "second", 1200L);
        verify(testDataService, never()).getCurrentVersion(anyLong());
        assertEquals(JudgeStatus.STATUS_ACCEPTED, result().getStatus());
        assertEquals(2, result().getTestCaseResults().size());
        verify(container).remove(true);
    }

    @Test
    void oldQueuedTaskReadsLegacyManifestWithoutCreatingOrChangingVersion() {
        JudgeTask task = task(JudgeConstants.DEFAULT_JUDGE, JudgeConstants.ACM);
        task.setTestDataVersionId(null);
        when(testDataService.getManifest(10L, null)).thenReturn(manifest(null, 1));
        when(testDataService.openFile(10L, null, "/in/1.in")).thenReturn(stream("legacy\n"));
        when(testDataService.openFile(10L, null, "/out/1.out")).thenReturn(stream("answer"));
        when(sandBox.execute(container, "legacy\n", 1200L))
                .thenReturn(new CodeRunResult("answer", null, 3L, 20L));

        runWithSandbox(task);

        assertEquals(JudgeStatus.STATUS_ACCEPTED, result().getStatus());
        verify(testDataService, never()).getCurrentVersion(anyLong());
    }

    @Test
    void manifestForDifferentVersionFailsAsSystemErrorBeforeStartingSandbox() {
        JudgeTask task = task(JudgeConstants.DEFAULT_JUDGE, JudgeConstants.ACM);
        when(testDataService.getManifest(10L, 77L)).thenReturn(manifest(88L, 1));

        judgeRun.execute(task);

        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, result().getStatus());
        verifyNoInteractions(dockerClient, container, sandBox);
        verify(testDataService, never()).openFile(anyLong(), anyLong(), anyString());
    }

    @Test
    void configuredResultRouteDoesNotPublishIntoLegacyExchange() {
        ReflectionTestUtils.setField(judgeRun, "judgeExchange", "judge.storage-v1.topic");
        ReflectionTestUtils.setField(judgeRun, "judgeResultRoutingKey", "storage-v1.judge.result");
        JudgeTask task = task(JudgeConstants.DEFAULT_JUDGE, JudgeConstants.ACM);
        when(testDataService.getManifest(10L, 77L)).thenReturn(manifest(88L, 1));

        judgeRun.execute(task);

        ArgumentCaptor<JudgeResult> result = ArgumentCaptor.forClass(JudgeResult.class);
        verify(messageQueueUtils).send(eq("judge.storage-v1.topic"), eq("storage-v1.judge.result"), result.capture());
        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, result.getValue().getStatus());
        verify(messageQueueUtils, never()).send(
                eq(RabbitMQConstants.JUDGE_EXCHANGE), eq(RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY), any());
    }

    @Test
    void fileIntegrityFailureClosesStreamAndProducesSystemError() throws IOException {
        JudgeTask task = task(JudgeConstants.DEFAULT_JUDGE, JudgeConstants.ACM);
        when(testDataService.getManifest(10L, 77L)).thenReturn(manifest(77L, 1));
        InputStream corrupted = org.mockito.Mockito.mock(InputStream.class);
        when(corrupted.readAllBytes()).thenThrow(new IOException("SHA-256 mismatch"));
        when(testDataService.openFile(10L, 77L, "/in/1.in")).thenReturn(corrupted);

        runWithSandbox(task);

        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, result().getStatus());
        verify(corrupted).close();
        verify(sandBox, never()).execute(any(), anyString(), anyLong());
    }

    @Test
    void selfTestPassesWhitespaceUnchangedAndUsesOnlyInlineData() {
        JudgeTask task = task(JudgeConstants.TEST_JUDGE, JudgeConstants.ACM);
        task.setInput(" \tleading\r\ntrailing  \n\n");
        task.setExpectedOutput("answer\n");
        when(sandBox.execute(container, task.getInput(), 1200L))
                .thenReturn(new CodeRunResult("answer\n", null, 3L, 20L));

        runWithSandbox(task);

        verify(sandBox).execute(container, task.getInput(), 1200L);
        verifyNoInteractions(testDataService, messageQueueUtils);
        ArgumentCaptor<JudgeResultVO> statuses = ArgumentCaptor.forClass(JudgeResultVO.class);
        verify(redisCache, org.mockito.Mockito.times(3)).setCacheObject(
                eq(CacheConstants.JUDGE_KEY + 100L), statuses.capture(), eq(5), eq(TimeUnit.MINUTES));
        assertEquals(JudgeStatus.STATUS_ACCEPTED, statuses.getValue().getStatus());
    }

    @Test
    void compileFailureRemainsCompileError() {
        JudgeTask task = task(JudgeConstants.TEST_JUDGE, JudgeConstants.ACM);
        doThrow(new CompileException("syntax error")).when(sandBox).compile(container);

        runWithSandbox(task);

        assertEquals(JudgeStatus.STATUS_COMPILE_ERROR, finalCachedStatus().getStatus());
        assertEquals("syntax error", finalCachedStatus().getErrorMessage());
    }

    @Test
    void sandboxInfrastructureFailureIsSystemError() {
        JudgeTask task = task(JudgeConstants.TEST_JUDGE, JudgeConstants.ACM);
        doThrow(new SandBoxException("Docker unavailable")).when(sandBox).compile(container);

        runWithSandbox(task);

        assertEquals(JudgeStatus.STATUS_SYSTEM_ERROR, finalCachedStatus().getStatus());
    }

    @Test
    void oiScoringAndOutputComparisonRemainCompatible() {
        JudgeTask task = task(JudgeConstants.DEFAULT_JUDGE, JudgeConstants.OI);
        when(testDataService.getManifest(10L, 77L)).thenReturn(manifest(77L, 2));
        for (int id = 1; id <= 2; id++) {
            when(testDataService.openFile(10L, 77L, "/in/" + id + ".in"))
                    .thenReturn(stream("test\n"));
            when(testDataService.openFile(10L, 77L, "/out/" + id + ".out"))
                    .thenReturn(stream("answer\n"));
        }
        when(sandBox.execute(container, "test\n", 1200L))
                .thenReturn(new CodeRunResult("answer  \r\n\r\n", null, 3L, 20L))
                .thenReturn(new CodeRunResult("wrong", null, 5L, 30L));

        runWithSandbox(task);

        JudgeResult result = result();
        assertEquals(JudgeStatus.STATUS_PARTIAL_ACCEPTED, result.getStatus());
        assertEquals(50, result.getScore());
        assertEquals(5L, result.getTime());
        assertEquals(30L, result.getMemory());
        assertTrue(result.getTestCaseResults().stream()
                .anyMatch(testCase -> testCase.getStatus() == JudgeStatus.STATUS_WRONG_ANSWER));
    }

    private void runWithSandbox(JudgeTask task) {
        try (MockedConstruction<CodeExecContainerFactory> containers = mockConstruction(
                CodeExecContainerFactory.class,
                (factory, context) -> when(factory.createDockerContainer()).thenReturn(container));
             MockedConstruction<SandBoxFactory> sandboxes = mockConstruction(
                     SandBoxFactory.class,
                     (factory, context) -> when(factory.createSandBox(container)).thenReturn(sandBox))) {
            judgeRun.execute(task);
        }
    }

    private JudgeResult result() {
        ArgumentCaptor<JudgeResult> result = ArgumentCaptor.forClass(JudgeResult.class);
        verify(messageQueueUtils).send(
                eq(RabbitMQConstants.JUDGE_EXCHANGE),
                eq(RabbitMQConstants.JUDGE_RESULT_ROUTING_KEY), result.capture());
        return result.getValue();
    }

    private JudgeResultVO finalCachedStatus() {
        ArgumentCaptor<JudgeResultVO> statuses = ArgumentCaptor.forClass(JudgeResultVO.class);
        verify(redisCache, org.mockito.Mockito.atLeastOnce()).setCacheObject(
                eq(CacheConstants.JUDGE_KEY + 100L), statuses.capture(), eq(5), eq(TimeUnit.MINUTES));
        return statuses.getValue();
    }

    private JudgeTask task(Integer strategy, Integer mode) {
        Language language = new Language();
        language.setName("Java");
        language.setEnv("judge-test-env");
        return JudgeTask.builder()
                .submissionId(100L)
                .problemId(10L)
                .testDataVersionId(77L)
                .strategy(strategy)
                .mode(mode)
                .languageConfig(language)
                .code("class Main {}")
                .input("test\n")
                .expectedOutput("answer\n")
                .timeLimit(1000L)
                .memoryLimit(262144L)
                .build();
    }

    private TestDataManifest manifest(Long versionId, int count) {
        TestDataManifest manifest = new TestDataManifest();
        manifest.setProblemId(10L);
        manifest.setVersionId(versionId);
        manifest.setEntries(java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(id -> {
                    TestDataManifest.Entry entry = new TestDataManifest.Entry();
                    entry.setId(id);
                    entry.setInput("/in/" + id + ".in");
                    entry.setOutput("/out/" + id + ".out");
                    return entry;
                })
                .toList());
        return manifest;
    }

    private InputStream stream(String data) {
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }
}
