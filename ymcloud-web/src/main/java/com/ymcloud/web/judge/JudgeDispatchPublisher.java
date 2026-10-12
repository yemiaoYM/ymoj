package com.ymcloud.web.judge;

import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.entity.JudgeDispatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 数据库任务箱补偿发布，使用短租约、mandatory 返回和关联确认，恢复次数有界。 */
@Slf4j
@Component
public class JudgeDispatchPublisher {

    private final JudgeDispatchEntityService dispatches;
    private final JudgeResultService results;
    private final RabbitTemplate rabbitTemplate;
    private final JudgeMessageCodec codec;
    private final JudgeDispatchProperties properties;
    private final String exchange;
    private final String routingKey;

    public JudgeDispatchPublisher(JudgeDispatchEntityService dispatches, JudgeResultService results,
                                  @Qualifier("judgeRabbitTemplate") RabbitTemplate rabbitTemplate,
                                  JudgeMessageCodec codec, JudgeDispatchProperties properties,
                                  @Value("${ymcloud.judge.exchange:" + JudgeProtocol.EXCHANGE + "}") String exchange,
                                  @Value("${ymcloud.judge.task-routing-key:" + JudgeProtocol.TASK_ROUTE + "}")
                                  String routingKey) {
        this.dispatches = dispatches;
        this.results = results;
        this.rabbitTemplate = rabbitTemplate;
        this.codec = codec;
        this.properties = properties;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Scheduled(fixedDelayString = "${ymcloud.judge.dispatch.poll-interval-ms:1000}")
    public void tick() {
        try {
            expireTasks();
            recoverTasks();
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            List<JudgeDispatch> ready = dispatches.lambdaQuery()
                    .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.READY)
                    .le(JudgeDispatch::getNextAttemptTime, now)
                    .and(query -> query.isNull(JudgeDispatch::getLeaseUntil)
                            .or()
                            .lt(JudgeDispatch::getLeaseUntil, now))
                    .orderByAsc(JudgeDispatch::getNextAttemptTime)
                    .last("LIMIT " + properties.getBatchSize())
                    .list();
            for (JudgeDispatch dispatch : ready) {
                publish(dispatch);
            }
        } catch (RuntimeException e) {
            log.warn("判题任务箱暂时不可用，保留数据库任务等待下次恢复");
        }
    }

    /** 此方法不在业务提交事务中执行，只读取已经提交的任务。 */
    public void publish(JudgeDispatch dispatch) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!dispatch.getDeadlineTime().isAfter(now)) {
            results.expire(dispatch.getSubmissionId(), dispatch.getExecutionId());
            return;
        }
        if (dispatch.getPublishAttempts() >= properties.getMaxPublishAttempts()) {
            results.failPublishing(dispatch.getSubmissionId(), dispatch.getExecutionId(), "任务发布失败，重试次数已用尽");
            return;
        }
        String lease = UUID.randomUUID().toString();
        if (!claim(dispatch, lease, now)) {
            return;
        }
        try {
            JudgeTask task = readTask(dispatch);
            CorrelationData correlation = send(dispatch, task, lease);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                retry(dispatch, lease);
                return;
            }
            markWaiting(dispatch, lease);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retry(dispatch, lease);
        } catch (IllegalArgumentException e) {
            results.failPublishing(dispatch.getSubmissionId(), dispatch.getExecutionId(), "持久评测任务格式无效");
        } catch (Exception e) {
            retry(dispatch, lease);
        }
    }

    private boolean claim(JudgeDispatch dispatch, String lease, LocalDateTime now) {
        return dispatches.lambdaUpdate()
                .eq(JudgeDispatch::getId, dispatch.getId())
                .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.READY)
                .eq(JudgeDispatch::getPublishAttempts, dispatch.getPublishAttempts())
                .le(JudgeDispatch::getNextAttemptTime, now)
                .and(update -> update.isNull(JudgeDispatch::getLeaseUntil)
                        .or()
                        .lt(JudgeDispatch::getLeaseUntil, now))
                .set(JudgeDispatch::getLeaseToken, lease)
                .set(JudgeDispatch::getLeaseUntil, now.plus(properties.getLeaseDuration()))
                .set(JudgeDispatch::getPublishAttempts, dispatch.getPublishAttempts() + 1)
                .update();
    }

    private JudgeTask readTask(JudgeDispatch dispatch) {
        JudgeTask task = codec.task(dispatch.getTaskPayload());
        if (task.getSchemaVersion() != JudgeProtocol.SCHEMA_VERSION
                || !Objects.equals(task.getExecutionId(), dispatch.getExecutionId())
                || !Objects.equals(task.getSubmissionId(), dispatch.getSubmissionId())
                || !Objects.equals(task.getStrategy(), dispatch.getStrategy())
                || !Objects.equals(task.getProblemId(), dispatch.getProblemId())
                || !Objects.equals(task.getTestDataVersionId(), dispatch.getTestDataVersionId())
                || task.getDeadlineEpochMs() == null) {
            throw new IllegalArgumentException("持久任务与发布标识不一致");
        }
        return task;
    }

    private CorrelationData send(JudgeDispatch dispatch, JudgeTask task, String lease) {
        CorrelationData correlation = new CorrelationData(dispatch.getExecutionId() + ":" + lease);
        rabbitTemplate.convertAndSend(exchange, routingKey, dispatch.getTaskPayload(), message -> {
            message.getMessageProperties().setMessageId(dispatch.getExecutionId());
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setContentEncoding("UTF-8");
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            long remaining = Math.max(1, task.getDeadlineEpochMs() - System.currentTimeMillis());
            message.getMessageProperties().setExpiration(Long.toString(remaining));
            return message;
        }, correlation);
        return correlation;
    }

    private void markWaiting(JudgeDispatch dispatch, String lease) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        dispatches.lambdaUpdate()
                .eq(JudgeDispatch::getId, dispatch.getId())
                .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                .eq(JudgeDispatch::getLeaseToken, lease)
                .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.READY)
                .set(JudgeDispatch::getDispatchState, JudgeDispatchState.WAITING)
                .set(JudgeDispatch::getPublishedTime, now)
                .set(JudgeDispatch::getNextAttemptTime, now.plus(properties.getResultWait()))
                .set(JudgeDispatch::getLeaseToken, null)
                .set(JudgeDispatch::getLeaseUntil, null)
                .update();
    }

    private void retry(JudgeDispatch dispatch, String lease) {
        LocalDateTime nextAttempt = LocalDateTime.now(ZoneOffset.UTC).plus(properties.getRetryInterval());
        boolean reset = dispatches.lambdaUpdate()
                .eq(JudgeDispatch::getId, dispatch.getId())
                .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                .eq(JudgeDispatch::getLeaseToken, lease)
                .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.READY)
                .set(JudgeDispatch::getLeaseToken, null)
                .set(JudgeDispatch::getLeaseUntil, null)
                .set(JudgeDispatch::getNextAttemptTime, nextAttempt)
                .update();
        if (reset && dispatch.getPublishAttempts() + 1 >= properties.getMaxPublishAttempts()) {
            results.failPublishing(dispatch.getSubmissionId(), dispatch.getExecutionId(), "任务发布失败，重试次数已用尽");
        }
        log.debug("判题发布未确认，持久任务保留待重试，提交编号：{}", dispatch.getSubmissionId());
    }

    private void expireTasks() {
        List<JudgeDispatch> expired = dispatches.lambdaQuery()
                .ne(JudgeDispatch::getDispatchState, JudgeDispatchState.COMPLETED)
                .le(JudgeDispatch::getDeadlineTime, LocalDateTime.now(ZoneOffset.UTC))
                .orderByAsc(JudgeDispatch::getDeadlineTime)
                .last("LIMIT " + properties.getBatchSize())
                .list();
        for (JudgeDispatch dispatch : expired) {
            results.expire(dispatch.getSubmissionId(), dispatch.getExecutionId());
        }
    }

    private void recoverTasks() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<JudgeDispatch> waiting = dispatches.lambdaQuery()
                .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.WAITING)
                .le(JudgeDispatch::getNextAttemptTime, now)
                .gt(JudgeDispatch::getDeadlineTime, now)
                .orderByAsc(JudgeDispatch::getNextAttemptTime)
                .last("LIMIT " + properties.getBatchSize())
                .list();
        for (JudgeDispatch dispatch : waiting) {
            if (dispatch.getRecoveryAttempts() >= properties.getMaxRecoveryAttempts()) {
                dispatches.lambdaUpdate()
                        .eq(JudgeDispatch::getId, dispatch.getId())
                        .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                        .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.WAITING)
                        .eq(JudgeDispatch::getLastResultSequence, dispatch.getLastResultSequence())
                        .set(JudgeDispatch::getNextAttemptTime, dispatch.getDeadlineTime())
                        .update();
                continue;
            }
            dispatches.lambdaUpdate()
                    .eq(JudgeDispatch::getId, dispatch.getId())
                    .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                    .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.WAITING)
                    .eq(JudgeDispatch::getLastResultSequence, dispatch.getLastResultSequence())
                    .eq(JudgeDispatch::getRecoveryAttempts, dispatch.getRecoveryAttempts())
                    .le(JudgeDispatch::getNextAttemptTime, now)
                    .set(JudgeDispatch::getDispatchState, JudgeDispatchState.READY)
                    .set(JudgeDispatch::getRecoveryAttempts, dispatch.getRecoveryAttempts() + 1)
                    .set(JudgeDispatch::getNextAttemptTime, now)
                    .set(JudgeDispatch::getLeaseToken, null)
                    .set(JudgeDispatch::getLeaseUntil, null)
                    .update();
        }
    }
}
