package com.ymcloud.judge;

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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkerMessageLifecycleTest {

    ObjectMapper mapper;
    WorkerProperties properties;
    WorkerExecutionStore store;
    WorkerExecutionStore.Lease lease;
    ResultJournal journal;
    ResultPublisher publisher;
    JudgeRun runner;
    JudgeListener listener;
    Channel channel;
    JudgeTask task;
    JudgeResult result;

    @BeforeEach
    void setup() {
        mapper = new WorkerAmqpConfig().workerObjectMapper();
        properties = new WorkerProperties();
        store = mock(WorkerExecutionStore.class);
        lease = mock(WorkerExecutionStore.Lease.class);
        journal = mock(ResultJournal.class);
        publisher = mock(ResultPublisher.class);
        runner = mock(JudgeRun.class);
        channel = mock(Channel.class);
        task = WorkerFixtures.task(1, 0);
        result = JudgeResult.builder().executionId(task.getExecutionId()).submissionId(task.getSubmissionId())
                .problemId(task.getProblemId()).testDataVersionId(task.getTestDataVersionId())
                .strategy(task.getStrategy()).terminal(true).sequence(3).status(JudgeVerdict.STATUS_ACCEPTED).build();
        listener = new JudgeListener(mapper, new WorkerTaskValidator(properties), store, journal,
                publisher, runner, properties, new SimpleMeterRegistry());
    }

    @Test
    void successfulTaskIsJournaledAndConfirmedBeforeAckAndTypeHeaderIsIgnored() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(lease, null, false, "digest"));
        when(lease.attempt()).thenReturn(1);
        when(runner.execute(any(), any())).thenReturn(result);
        Message message = message();
        message.getMessageProperties().setHeader("__TypeId__", "java.lang.Runtime");
        listener.listenJudge(message, channel);
        var order = inOrder(journal, publisher, lease, channel);
        order.verify(journal).read(task.getExecutionId(), "digest");
        order.verify(lease).requireOwned();
        order.verify(journal).save(task.getExecutionId(), "digest", result);
        order.verify(lease).requireOwned();
        order.verify(publisher).publish(result);
        order.verify(lease).complete(result);
        order.verify(channel).basicAck(7L, false);
    }

    @Test
    void completedOrJournaledDeliveryDoesNotRunCodeAgain() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(null, result, false, "digest"));
        listener.listenJudge(message(), channel);
        verify(publisher).publish(result);
        verify(channel).basicAck(7L, false);
        verifyNoInteractions(runner, journal);
    }

    @Test
    void recoveredJournalResultIsResentAfterRestart() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(lease, null, false, "digest"));
        when(journal.read(task.getExecutionId(), "digest")).thenReturn(result);
        listener.listenJudge(message(), channel);
        verifyNoInteractions(runner);
        verify(publisher).publish(result);
        verify(lease).complete(result);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void corruptedJournalProducesSystemErrorInsteadOfPermanentRedelivery() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(lease, null, false, "digest"));
        when(journal.read(task.getExecutionId(), "digest")).thenThrow(new java.io.IOException("corrupt"));
        result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
        when(runner.systemFailure(any(), anyString())).thenReturn(result);
        listener.listenJudge(message(), channel);
        verify(runner, never()).execute(any(), any());
        verify(journal).save(task.getExecutionId(), "digest", result);
        verify(publisher).publish(result);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void unavailableResultAndRetryRoutesUseDeadLetterRatherThanHotRedelivery() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(null, result, false, "digest"));
        doThrow(new IllegalStateException("NO_ROUTE")).when(publisher).publish(result);
        doThrow(new IllegalStateException("broker down")).when(publisher).retry(task);
        listener.listenJudge(message(), channel);
        verify(channel).basicReject(7L, false);
        verify(channel, never()).basicNack(7L, false, true);
        verify(channel, never()).basicAck(7L, false);
    }

    @Test
    void concurrentLeaseDefersTaskAndInfrastructureRetriesAreBounded() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(null, null, true, "digest"));
        listener.listenJudge(message(), channel);
        verify(publisher).retry(task);
        verifyNoInteractions(runner);
    }

    @Test
    void lastInfrastructureAttemptPublishesSystemErrorAndPreservesIdentity() throws Exception {
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(lease, null, false, "digest"));
        when(lease.attempt()).thenReturn(3);
        when(runner.execute(any(), any())).thenThrow(new java.io.IOException("cache corrupt"));
        result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
        when(runner.systemFailure(any(), anyString())).thenReturn(result);
        listener.listenJudge(message(), channel);
        verify(publisher).publish(result);
        verify(publisher, never()).retry(any());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void invalidSchemaAndChangedDigestAreRejectedWithoutExecution() throws Exception {
        task.setSchemaVersion(1);
        listener.listenJudge(message(), channel);
        verify(channel).basicReject(7L, false);
        verifyNoInteractions(store, runner, publisher);
    }

    @Test
    void expiredTaskStopsEvenWhenRedisOrBrokerIsContinuouslyUnavailable() throws Exception {
        task.setSubmittedAtEpochMs(System.currentTimeMillis() - 2000);
        task.setDeadlineEpochMs(System.currentTimeMillis() - 1000);
        when(store.digest(task)).thenThrow(new IllegalStateException("unavailable"));
        result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
        when(runner.systemFailure(any(), anyString())).thenReturn(result);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(result);
        listener.listenJudge(message(), channel);
        verify(store, never()).claim(any());
        verify(publisher, never()).retry(any());
        verify(channel).basicReject(7L, false);
    }

    @Test
    void wrongRecoveredVersionIsReplacedWithCurrentIdentitySystemErrorWithoutRunningCode() throws Exception {
        JudgeResult corrupted = JudgeResult.builder().executionId(task.getExecutionId())
                .submissionId(task.getSubmissionId()).strategy(task.getStrategy())
                .testDataVersionId(999L).terminal(true).sequence(3).status(JudgeVerdict.STATUS_ACCEPTED).build();
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(null, corrupted, false, "digest"));
        result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
        when(runner.systemFailure(any(), anyString())).thenReturn(result);
        listener.listenJudge(message(), channel);
        verify(runner, never()).execute(any(), any());
        verify(journal).save(task.getExecutionId(), "digest", result);
        verify(store).repairCompleted(task, result);
        verify(publisher).publish(result);
        verify(publisher, never()).publish(corrupted);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void invalidRecoveredStatisticsOrCasePayloadIsRepairedBeforeAcknowledgement() throws Exception {
        List<JudgeResult> corruptedResults = new ArrayList<>();
        JudgeResult negativeTime = copyResult();
        negativeTime.setTime(-1L);
        corruptedResults.add(negativeTime);
        JudgeResult invalidScore = copyResult();
        invalidScore.setScore(101);
        corruptedResults.add(invalidScore);
        JudgeResult oversizedWorkerId = copyResult();
        oversizedWorkerId.setWorkerId("w".repeat(129));
        corruptedResults.add(oversizedWorkerId);
        JudgeResult negativeEmissionTime = copyResult();
        negativeEmissionTime.setEmittedAtEpochMs(-1L);
        corruptedResults.add(negativeEmissionTime);
        TestCaseResult testCase = new TestCaseResult();
        testCase.setCaseIndex(1);
        testCase.setStatus(JudgeVerdict.STATUS_ACCEPTED);
        JudgeResult duplicateCases = copyResult();
        duplicateCases.setTestCaseResults(List.of(testCase, testCase));
        corruptedResults.add(duplicateCases);
        TestCaseResult oversizedCase = new TestCaseResult();
        oversizedCase.setCaseIndex(1);
        oversizedCase.setStatus(JudgeVerdict.STATUS_ACCEPTED);
        oversizedCase.setOutput("x".repeat(65537));
        JudgeResult oversizedOutput = copyResult();
        oversizedOutput.setTestCaseResults(List.of(oversizedCase));
        corruptedResults.add(oversizedOutput);

        result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
        result.setScore(0);
        when(runner.systemFailure(any(), anyString())).thenReturn(result);
        for (JudgeResult corrupted : corruptedResults) {
            when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(null, corrupted, false, "digest"));
            listener.listenJudge(message(), channel);
            verify(publisher, never()).publish(corrupted);
        }
        verify(runner, never()).execute(any(), any());
        verify(journal, times(corruptedResults.size())).save(task.getExecutionId(), "digest", result);
        verify(store, times(corruptedResults.size())).repairCompleted(task, result);
        verify(publisher, times(corruptedResults.size())).publish(result);
        verify(channel, times(corruptedResults.size())).basicAck(7L, false);
    }

    @Test
    void parsedButInvalidJournalResultBecomesSystemErrorWithoutReexecutingCode() throws Exception {
        JudgeResult corrupted = copyResult();
        corrupted.setMemory(-1L);
        when(store.claim(task)).thenReturn(new WorkerExecutionStore.Claim(lease, null, false, "digest"));
        when(journal.read(task.getExecutionId(), "digest")).thenReturn(corrupted);
        result.setStatus(JudgeVerdict.STATUS_SYSTEM_ERROR);
        result.setScore(0);
        when(runner.systemFailure(any(), anyString())).thenReturn(result);

        listener.listenJudge(message(), channel);

        verify(runner, never()).execute(any(), any());
        verify(store, never()).repairCompleted(any(), any());
        verify(publisher, never()).publish(corrupted);
        var order = inOrder(journal, publisher, lease, channel);
        order.verify(journal).read(task.getExecutionId(), "digest");
        order.verify(lease).requireOwned();
        order.verify(journal).save(task.getExecutionId(), "digest", result);
        order.verify(lease).requireOwned();
        order.verify(publisher).publish(result);
        order.verify(lease).complete(result);
        order.verify(channel).basicAck(7L, false);
    }

    private JudgeResult copyResult() throws Exception {
        return mapper.readValue(mapper.writeValueAsBytes(result), JudgeResult.class);
    }

    private Message message() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        return new Message(mapper.writeValueAsBytes(task), properties);
    }
}
