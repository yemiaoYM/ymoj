package com.ymcloud.web.judge;

import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import com.ymcloud.pojo.entity.JudgeDispatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 数据库任务箱补偿发布，使用短租约、mandatory 返回和关联确认，恢复次数有界。
 */
@Slf4j
@Component
public class JudgeDispatchPublisher {

    @Autowired
    private JudgeDispatchEntityService dispatches;

    @Autowired
    private JudgeResultService results;

    @Autowired
    @Qualifier("judgeRabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JudgeMessageCodec codec;

    @Autowired
    private JudgeDispatchProperties properties;

    @Value("${ymcloud.judge.exchange:" + JudgeProtocol.EXCHANGE + "}")
    private String exchange;

    @Value("${ymcloud.judge.task-routing-key:" + JudgeProtocol.TASK_ROUTE + "}")
    private String routingKey;

    private boolean unavailable;

    /**
     * 按批次处理到期、恢复和待发布任务，数据库故障保留任务等待恢复。
     */
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
            if (unavailable) {
                unavailable = false;
                log.info("判题任务箱已恢复，继续发布和补偿数据库任务");
            }
        } catch (RuntimeException e) {
            if (!unavailable) {
                unavailable = true;
                log.warn("判题任务箱暂时不可用，保留数据库任务等待恢复，异常类型：{}", e.getClass().getSimpleName());
            }
        }
    }

    /**
     * 此方法不在业务提交事务中执行，只读取已经提交的任务。
     * @param dispatch 已持久化的任务快照
     */
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
                retry(dispatch, lease, correlation.getReturned() == null ? "Broker 未确认" : "消息无法路由");
                return;
            }
            markWaiting(dispatch, lease);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retry(dispatch, lease, "发布线程被中断");
        } catch (IllegalArgumentException e) {
            log.warn("持久判题任务格式无效，提交编号：{}，执行编号：{}，数据版本：{}",
                    dispatch.getSubmissionId(), dispatch.getExecutionId(), dispatch.getTestDataVersionId());
            results.failPublishing(dispatch.getSubmissionId(), dispatch.getExecutionId(), "持久评测任务格式无效");
        } catch (Exception e) {
            retry(dispatch, lease, e.getClass().getSimpleName());
        }
    }

    /**
     * 通过状态、尝试次数和时间条件原子领取发布租约。
     * @param dispatch 待发布任务快照
     * @param lease 本次发布租约令牌
     * @param now 当前 UTC 时间
     * @return 是否成功领取且增加发布次数
     */
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

    /**
     * 解析持久任务并核对执行身份，拒绝错配快照
     * @param dispatch 包含持久 JSON 的任务记录
     * @return 与记录身份一致的任务
     */
    private JudgeTask readTask(JudgeDispatch dispatch) {
        JudgeTask task = codec.task(dispatch.getTaskPayload());
        if (!Objects.equals(task.getExecutionId(), dispatch.getExecutionId())
                || !Objects.equals(task.getSubmissionId(), dispatch.getSubmissionId())
                || !Objects.equals(task.getStrategy(), dispatch.getStrategy())
                || !Objects.equals(task.getProblemId(), dispatch.getProblemId())
                || !Objects.equals(task.getTestDataVersionId(), dispatch.getTestDataVersionId())
                || task.getDeadlineEpochMs() == null) {
            throw new IllegalArgumentException("持久任务与发布标识不一致");
        }
        return task;
    }

    /**
     * 发布持久消息并将剩余任务时间写入消息 TTL。
     * @param dispatch 任务箱记录
     * @param task 已验证的任务快照
     * @param lease 当前发布租约令牌
     * @return 用于等待 ACK 和检查退回的关联对象
     */
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

    /**
     * 确认后条件更新等待结果状态，不能覆盖已经到达的结果。
     * @param dispatch 当前任务快照
     * @param lease 当前发布租约令牌
     */
    private void markWaiting(JudgeDispatch dispatch, String lease) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean waiting = dispatches.lambdaUpdate()
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
        log.info("判题任务发布已确认，提交编号：{}，执行编号：{}，数据版本：{}，发布次数：{}，恢复次数：{}",
                dispatch.getSubmissionId(), dispatch.getExecutionId(), dispatch.getTestDataVersionId(),
                dispatch.getPublishAttempts() + 1, dispatch.getRecoveryAttempts());
        if (!waiting) {
            log.debug("判题任务等待状态未更新，使用数据库当前状态继续恢复，提交编号：{}，执行编号：{}",
                    dispatch.getSubmissionId(), dispatch.getExecutionId());
        }
    }

    /**
     * 仅持有当前租约时释放发布占用并安排有限重试。
     * @param dispatch 当前任务快照
     * @param lease 当前发布租约令牌
     * @param reason 已脱敏的失败原因
     */
    private void retry(JudgeDispatch dispatch, String lease, String reason) {
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
        if (reset) {
            log.warn("判题任务发布失败，提交编号：{}，执行编号：{}，数据版本：{}，发布次数：{}，原因：{}，下次尝试：{}",
                    dispatch.getSubmissionId(), dispatch.getExecutionId(), dispatch.getTestDataVersionId(),
                    dispatch.getPublishAttempts() + 1, reason, nextAttempt);
        }
    }

    /**
     * 按批次收敛超过总截止的任务，通过结果事务生成安全终态。
     */
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

    /**
     * 恢复等待超时的执行，保留快照并限制恢复次数。
     */
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
                boolean stopped = dispatches.lambdaUpdate()
                        .eq(JudgeDispatch::getId, dispatch.getId())
                        .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                        .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.WAITING)
                        .eq(JudgeDispatch::getLastResultSequence, dispatch.getLastResultSequence())
                        .set(JudgeDispatch::getNextAttemptTime, dispatch.getDeadlineTime())
                        .update();
                if (stopped) {
                    log.warn("判题任务恢复次数已用尽，等待总截止时间，提交编号：{}，执行编号：{}，恢复次数：{}，截止时间：{}",
                            dispatch.getSubmissionId(), dispatch.getExecutionId(), dispatch.getRecoveryAttempts(),
                            dispatch.getDeadlineTime());
                }
                continue;
            }
            boolean recovered = dispatches.lambdaUpdate()
                    .eq(JudgeDispatch::getId, dispatch.getId())
                    .eq(JudgeDispatch::getExecutionId, dispatch.getExecutionId())
                    .eq(JudgeDispatch::getDispatchState, JudgeDispatchState.WAITING)
                    .eq(JudgeDispatch::getLastResultSequence, dispatch.getLastResultSequence())
                    .eq(JudgeDispatch::getRecoveryAttempts, dispatch.getRecoveryAttempts())
                    .le(JudgeDispatch::getNextAttemptTime, now)
                    .set(JudgeDispatch::getDispatchState, JudgeDispatchState.READY)
                    .set(JudgeDispatch::getRecoveryAttempts, dispatch.getRecoveryAttempts() + 1)
                    // DATETIME 未保留小数秒，避免向上舍入后本轮调度无法立即读取恢复任务。
                    .set(JudgeDispatch::getNextAttemptTime, now.withNano(0))
                    .set(JudgeDispatch::getLeaseToken, null)
                    .set(JudgeDispatch::getLeaseUntil, null)
                    .update();
            if (recovered) {
                log.warn("判题结果等待超时，准备恢复任务，提交编号：{}，执行编号：{}，数据版本：{}，恢复次数：{}",
                        dispatch.getSubmissionId(), dispatch.getExecutionId(), dispatch.getTestDataVersionId(),
                        dispatch.getRecoveryAttempts() + 1);
            }
        }
    }
}
