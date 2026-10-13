package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.strategy.JudgeRun;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import com.ymcloud.pojo.contract.judge.TestCaseResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 原任务仅在最终结果确认后 ACK；消息重复投递复用租约或已保存结果。
 */
@Slf4j
@Component
public class JudgeListener {

    private static final int MAX_TASK_MESSAGE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_TEST_CASES = 10000;
    private static final int MAX_RECOVERED_PREVIEW_CHARACTERS = 2 * 1024 * 1024;
    private static final Set<JudgeVerdict> TERMINAL = Set.of(
            JudgeVerdict.STATUS_CANCELLED,
            JudgeVerdict.STATUS_PRESENTATION_ERROR,
            JudgeVerdict.STATUS_COMPILE_ERROR,
            JudgeVerdict.STATUS_WRONG_ANSWER,
            JudgeVerdict.STATUS_ACCEPTED,
            JudgeVerdict.STATUS_TIME_LIMIT_EXCEEDED,
            JudgeVerdict.STATUS_MEMORY_LIMIT_EXCEEDED,
            JudgeVerdict.STATUS_RUNTIME_ERROR,
            JudgeVerdict.STATUS_SYSTEM_ERROR,
            JudgeVerdict.STATUS_PARTIAL_ACCEPTED);

    private final ObjectMapper mapper;
    private final WorkerTaskValidator validator;
    private final WorkerExecutionStore store;
    private final ResultJournal journal;
    private final ResultPublisher publisher;
    private final JudgeRun runner;
    private final WorkerProperties properties;
    private final MeterRegistry metrics;

    public JudgeListener(ObjectMapper mapper,
                         WorkerTaskValidator validator,
                         WorkerExecutionStore store,
                         ResultJournal journal,
                         ResultPublisher publisher,
                         JudgeRun runner,
                         WorkerProperties properties,
                         MeterRegistry metrics) {
        this.mapper = mapper;
        this.validator = validator;
        this.store = store;
        this.journal = journal;
        this.publisher = publisher;
        this.runner = runner;
        this.properties = properties;
        this.metrics = metrics;
    }

    @RabbitListener(queues = "${ymcloud.judge.task-queue:" + JudgeProtocol.TASK_QUEUE + "}",
            containerFactory = "workerListenerFactory")
    public void listenJudge(Message message, Channel channel) throws IOException {
        long delivery = message.getMessageProperties().getDeliveryTag();
        JudgeTask task;
        try {
            if (message.getBody().length > MAX_TASK_MESSAGE_BYTES) {
                throw new IllegalArgumentException("任务消息过大");
            }
            task = mapper.readValue(message.getBody(), JudgeTask.class);
            validator.validate(task);
        } catch (Exception e) {
            metrics.counter("ymcloud.judge.task.invalid").increment();
            log.warn("拒绝无效的 Worker 消息 delivery={} bytes={} exception={}",
                    delivery,
                    message.getBody().length,
                    e.getClass().getSimpleName());
            channel.basicReject(delivery, false);
            return;
        }
        Timer.Sample sample = Timer.start(metrics);
        log.info("Worker 接收评测任务 submission={} execution={} version={} strategy={} mode={} redelivered={}",
                task.getSubmissionId(),
                task.getExecutionId(),
                task.getTestDataVersionId(),
                task.getStrategy(),
                task.getMode(), message.getMessageProperties().isRedelivered());
        metrics.timer("ymcloud.judge.queue.wait").record(Math.max(0, System.currentTimeMillis() - task.getSubmittedAtEpochMs()), TimeUnit.MILLISECONDS);
        try {
            if (expired(task)) {
                finishExpired(task, channel, delivery);
                return;
            }
            processClaim(task, store.claim(task), channel, delivery);
        } catch (IllegalArgumentException e) {
            log.warn("拒绝内容无效或任务指纹不一致的消息 submission={} execution={} version={} exception={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId(),
                    e.getClass().getSimpleName());
            channel.basicReject(delivery, false);
        } catch (Exception e) {
            Thread.interrupted();
            metrics.counter("ymcloud.judge.message.failures").increment();
            log.warn("Worker 消息处理待恢复 submission={} execution={} version={} exception={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId(),
                    e.getClass().getSimpleName());
            if (expired(task)) {
                finishExpired(task, channel, delivery);
                return;
            }
            // broker 可用时进入有界延时队列，避免 Redis 故障导致热重投。
            try {
                publisher.retry(task);
                channel.basicAck(delivery, false);
            } catch (RuntimeException publishFailure) {
                // 延时路由也不可用时交给死信和 Web outbox 恢复，不能立即重入相同故障。
                log.warn("Worker 消息进入死信等待恢复 submission={} execution={} version={} exception={}",
                        task.getSubmissionId(),
                        task.getExecutionId(),
                        task.getTestDataVersionId(),
                        publishFailure.getClass().getSimpleName());
                channel.basicReject(delivery, false);
            }
        } finally {
            sample.stop(metrics.timer("ymcloud.judge.execution"));
        }
    }

    private void processClaim(JudgeTask task,
                              WorkerExecutionStore.Claim claim,
                              Channel channel,
                              long delivery) throws Exception {
        if (claim.busy()) {
            log.debug("Worker 重复任务等待执行租约 submission={} execution={} version={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId());
            if (expired(task)) {
                finishExpired(task, channel, delivery);
            } else {
                publisher.retry(task);
                channel.basicAck(delivery, false);
            }
            return;
        }
        if (claim.completed() != null) {
            metrics.counter("ymcloud.judge.task.duplicate").increment();
            log.debug("Worker 重复任务复用已完成结果 submission={} execution={} version={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId());
            JudgeResult completed = recovered(task, claim.completed());
            if (completed != claim.completed()) {
                journal.save(task.getExecutionId(), claim.digest(), completed);
                store.repairCompleted(task, completed);
            }
            publisher.publish(completed);
            channel.basicAck(delivery, false);
            return;
        }
        processLease(task, claim, channel, delivery);
    }

    private void processLease(JudgeTask task,
                              WorkerExecutionStore.Claim claim,
                              Channel channel,
                              long delivery) throws Exception {
        long started = System.nanoTime();
        try (WorkerExecutionStore.Lease lease = claim.lease()) {
            log.info("Worker 开始处理评测任务 submission={} execution={} version={} attempt={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId(),
                    lease.attempt());
            JudgeResult result = readJournal(task, claim.digest(), lease);
            if (result == null) {
                try {
                    if (expired(task) || lease.attempt() > properties.getMaxAttempts()) {
                        log.warn("Worker 任务达到执行边界 submission={} execution={} version={} attempt={}",
                                task.getSubmissionId(),
                                task.getExecutionId(),
                                task.getTestDataVersionId(),
                                lease.attempt());
                        result = runner.systemFailure(task, "评测任务超过截止时间或重试次数");
                    } else {
                        result = runner.execute(task, progress -> {
                            lease.requireOwned();
                            publisher.publish(progress);
                        });
                    }
                } catch (Exception e) {
                    // 截止时间中断不应使后续终态发送一直保留中断标志。
                    Thread.interrupted();
                    lease.requireOwned();
                    if (lease.attempt() < properties.getMaxAttempts() && !expired(task)) {
                        metrics.counter("ymcloud.judge.task.retry").increment();
                        log.warn("Worker 执行失败，准备延时重试 submission={} execution={} version={} attempt={} exception={}",
                                task.getSubmissionId(),
                                task.getExecutionId(),
                                task.getTestDataVersionId(),
                                lease.attempt(),
                                e.getClass().getSimpleName());
                        publisher.retry(task);
                        channel.basicAck(delivery, false);
                        return;
                    }
                    log.warn("Worker 执行失败，产生系统错误 submission={} execution={} version={} attempt={} exception={}",
                            task.getSubmissionId(), task.getExecutionId(), task.getTestDataVersionId(),
                            lease.attempt(), e.getClass().getSimpleName());
                    result = runner.systemFailure(task, "评测系统异常，请联系管理员");
                }
                lease.requireOwned();
                journal.save(task.getExecutionId(), claim.digest(), result);
            }
            lease.requireOwned();
            publisher.publish(result);
            lease.complete(result);
            if (result.getStatus() == JudgeVerdict.STATUS_SYSTEM_ERROR) {
                metrics.counter("ymcloud.judge.system.errors").increment();
                log.warn("Worker 评测终态为系统错误 submission={} execution={} version={} attempt={}",
                        task.getSubmissionId(),
                        task.getExecutionId(),
                        task.getTestDataVersionId(),
                        lease.attempt());
            }
            channel.basicAck(delivery, false);
            log.info("Worker 评测任务处理完成 submission={} execution={} version={} attempt={} status={} elapsedMs={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId(),
                    lease.attempt(),
                    result.getStatus(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private JudgeResult readJournal(JudgeTask task,
                                    String digest,
                                    WorkerExecutionStore.Lease lease)
            throws IOException {
        JudgeResult result;
        try {
            result = journal.read(task.getExecutionId(), digest);
        } catch (IOException corruptedJournal) {
            // 损坏结果不会反复执行或永久重投，使用当前任务身份生成系统错误并重写。
            log.warn("Worker 本地终态记录恢复失败 submission={} execution={} version={} exception={}",
                    task.getSubmissionId(), task.getExecutionId(), task.getTestDataVersionId(),
                    corruptedJournal.getClass().getSimpleName());
            result = runner.systemFailure(task, "评测结果恢复失败，请联系管理员");
            lease.requireOwned();
            journal.save(task.getExecutionId(), digest, result);
            return result;
        }
        if (result == null) {
            return null;
        }
        log.info("Worker 恢复本地终态记录 submission={} execution={} version={} attempt={}",
                task.getSubmissionId(), task.getExecutionId(), task.getTestDataVersionId(), lease.attempt());
        JudgeResult validated = recovered(task, result);
        if (validated != result) {
            lease.requireOwned();
            journal.save(task.getExecutionId(), digest, validated);
        }
        return validated;
    }

    private boolean expired(JudgeTask task) {
        return System.currentTimeMillis() >= task.getDeadlineEpochMs();
    }

    /**
     * 总截止时间后不依赖 Redis 恢复租约，有限发送终态或进入死信，避免无限延时重投。
     */
    private void finishExpired(JudgeTask task, Channel channel, long delivery) throws IOException {
        log.warn("Worker 恢复过期任务终态 submission={} execution={} version={}",
                task.getSubmissionId(), task.getExecutionId(), task.getTestDataVersionId());
        JudgeResult result = null;
        try {
            result = journal.read(task.getExecutionId(), store.digest(task));
        } catch (Exception unavailableJournal) {
            log.warn("过期执行无法恢复本地结果 submission={} execution={} version={} exception={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId(),
                    unavailableJournal.getClass().getSimpleName());
        }
        if (result == null) {
            result = runner.systemFailure(task, "评测任务超过总截止时间");
        } else {
            JudgeResult validated = recovered(task, result);
            if (validated != result) {
                try {
                    journal.save(task.getExecutionId(), store.digest(task), validated);
                } catch (Exception unavailableJournal) {
                    log.warn("过期执行无法修复本地结果 submission={} execution={} version={} exception={}",
                            task.getSubmissionId(),
                            task.getExecutionId(),
                            task.getTestDataVersionId(),
                            unavailableJournal.getClass().getSimpleName());
                }
            }
            result = validated;
        }
        try {
            publisher.publish(result);
            channel.basicAck(delivery, false);
            log.info("Worker 过期任务终态确认完成 submission={} execution={} version={} status={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId(),
                    result.getStatus());
        } catch (RuntimeException unavailableBroker) {
            // Web 持久化超时恢复负责最终收敛；已经过期的任务不再回流到重试队列。
            log.warn("Worker 过期任务进入死信等待恢复 submission={} execution={} version={} exception={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId(),
                    unavailableBroker.getClass().getSimpleName());
            channel.basicReject(delivery, false);
        }
    }

    private JudgeResult recovered(JudgeTask task, JudgeResult result) {
        boolean valid = result.getSchemaVersion() == JudgeProtocol.SCHEMA_VERSION
                && result.isTerminal() && result.getSequence() == 3 && result.getStatus() != null
                && TERMINAL.contains(result.getStatus())
                && Objects.equals(task.getExecutionId(), result.getExecutionId())
                && Objects.equals(task.getSubmissionId(), result.getSubmissionId())
                && Objects.equals(task.getProblemId(), result.getProblemId())
                && Objects.equals(task.getTestDataVersionId(), result.getTestDataVersionId())
                && Objects.equals(task.getStrategy(), result.getStrategy())
                && validRecoveredPayload(result);
        if (!valid) {
            log.warn("恢复结果身份或协议损坏 submission={} execution={} version={}",
                    task.getSubmissionId(),
                    task.getExecutionId(),
                    task.getTestDataVersionId());
            return runner.systemFailure(task, "评测结果恢复校验失败，请联系管理员");
        }
        return result;
    }

    /**
     * 恢复记录必须仍能通过 Web 的结果协议校验，防止已 ACK 的损坏结果反复被 Web 拒绝。
     */
    private boolean validRecoveredPayload(JudgeResult result) {
        if (!nonNegative(result.getTime()) || !nonNegative(result.getMemory())
                || !nonNegative(result.getEmittedAtEpochMs()) || length(result.getWorkerId()) > 128
                || !validScore(result.getScore()) || length(result.getErrorMessage()) > 16384) {
            return false;
        }
        List<TestCaseResult> cases = result.getTestCaseResults();
        if (cases == null) {
            return true;
        }
        if (cases.size() > MAX_TEST_CASES) {
            return false;
        }
        Set<Integer> indices = new HashSet<>();
        long previewCharacters = length(result.getErrorMessage());
        int outputLimit = result.getStrategy() == 1 ? 65536 : 4096;
        for (TestCaseResult testCase : cases) {
            if (testCase == null || testCase.getCaseIndex() == null || testCase.getCaseIndex() <= 0
                    || !indices.add(testCase.getCaseIndex()) || testCase.getStatus() == null
                    || !TERMINAL.contains(testCase.getStatus())
                    || !nonNegative(testCase.getTime()) || !nonNegative(testCase.getMemory())
                    || !validScore(testCase.getScore())
                    || (testCase.getJudgeId() != null
                    && !Objects.equals(result.getSubmissionId(), testCase.getJudgeId()))
                    || length(testCase.getErrorMessage()) > 4096
                    || length(testCase.getOutput()) > outputLimit) {
                return false;
            }
            previewCharacters += length(testCase.getErrorMessage()) + length(testCase.getOutput());
            if (previewCharacters > MAX_RECOVERED_PREVIEW_CHARACTERS) {
                return false;
            }
        }
        return true;
    }

    private boolean nonNegative(Long value) {
        return value == null || value >= 0;
    }

    private boolean validScore(Integer score) {
        return score == null || (score >= 0 && score <= 100);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
