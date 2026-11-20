package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.pojo.bo.judge.JudgeResult;
import com.ymcloud.pojo.bo.judge.JudgeTask;
import jakarta.annotation.PreDestroy;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: Redis 原子租约和任务指纹，只有当前租约持有者可以提交最终结果。
 */
@Component
@Slf4j
public class WorkerExecutionStore {

    private static final int MAX_COMPLETED_JSON_CHARACTERS = 16 * 1024 * 1024;

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local digest = redis.call('HGET', KEYS[1], 'digest')
            if digest and digest ~= ARGV[1] then return -1 end
            if redis.call('HGET', KEYS[1], 'result') then return 2 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            redis.call('HSET', KEYS[1], 'digest', ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('PSETEX', KEYS[2], ARGV[3], ARGV[2])
            return 10 + redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            return redis.call('DEL', KEYS[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            redis.call('HSET', KEYS[1], 'result', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REPAIR = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[1] then return 0 end
            if not redis.call('HGET', KEYS[1], 'result') then return 0 end
            redis.call('HSET', KEYS[1], 'result', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final WorkerProperties properties;
    private final ScheduledThreadPoolExecutor heartbeat;
    private final ScheduledThreadPoolExecutor deadlines;

    /**
     * 构造时建立独立续租和截止调度器，保留构造器注入。
     * @param redis Worker Redis 连接
     * @param mapper 判题专用映射器
     * @param properties 并发、租约及恢复配置
     */
    public WorkerExecutionStore(StringRedisTemplate redis, ObjectMapper mapper, WorkerProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
        this.heartbeat = new ScheduledThreadPoolExecutor(properties.getConcurrency(), runnable -> {
            Thread thread = new Thread(runnable, "judge-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeat.setRemoveOnCancelPolicy(true);
        this.deadlines = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "judge-task-deadline");
            thread.setDaemon(true);
            return thread;
        });
        this.deadlines.setRemoveOnCancelPolicy(true);
    }

    /**
     * 原子领取执行租约，重复任务返回忙碌或已完成分支。
     * @param task 已通过入口校验的任务
     * @return 领取结果及原任务摘要
     * @throws Exception 任务摘要计算、存储或调度失败
     */
    public Claim claim(JudgeTask task) throws Exception {
        String digest = digest(task);
        String state = key(task.getExecutionId(), "state");
        String lock = key(task.getExecutionId(), "lease");
        String owner = properties.getId() + ":" + UUID.randomUUID();
        Long status = redis.execute(ACQUIRE, List.of(state, lock), digest, owner,
                String.valueOf(properties.getLeaseMillis()), String.valueOf(properties.getRetentionMillis()));
        if (status == null) {
            throw new IllegalStateException("Worker 执行租约没有返回状态");
        }
        if (status < 0) {
            throw new IllegalArgumentException("同一执行标识收到不同任务内容");
        }
        if (status == 0) {
            log.debug("Worker 执行租约已被占用 submission={} execution={}",
                    task.getSubmissionId(), task.getExecutionId());
            return new Claim(null, null, true, digest);
        }
        if (status == 2) {
            Object value = redis.opsForHash().get(state, "result");
            if (value == null) {
                throw new IllegalStateException("已完成执行记录缺少结果");
            }
            return new Claim(null, readCompleted(value.toString()), false, digest);
        }
        if (status < 11 || status - 10 > Integer.MAX_VALUE) {
            throw new IllegalStateException("Worker 执行租约返回无效状态");
        }
        log.debug("Worker 获取执行租约 submission={} execution={} attempt={}",
                task.getSubmissionId(), task.getExecutionId(), status - 10);
        return new Claim(new Lease(task, state, lock, owner, (int) (status - 10)), null, false, digest);
    }

    /**
     * 解析有界恢复记录，损坏时返回待入口修复的空结果。
     * @param json 持久结果 JSON
     * @return 恢复结果或表示损坏的空结果对象
     */
    private JudgeResult readCompleted(String json) {
        if (json.length() > MAX_COMPLETED_JSON_CHARACTERS) {
            return new JudgeResult();
        }
        try {
            JudgeResult recovered = mapper.readValue(json, JudgeResult.class);
            return recovered == null ? new JudgeResult() : recovered;
        } catch (IOException corruptedResult) {
            // 交给消费边界产生当前任务身份的系统错误，并修复持久记录。
            return new JudgeResult();
        }
    }

    /**
     * 计算固定任务快照的 SHA-256，用于重复投递核对。
     * @param task 完整任务快照
     * @return 小写 SHA-256
     * @throws Exception 任务序列化或摘要计算失败
     */
    public String digest(JudgeTask task) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(task)));
    }

    /**
     * 已完成恢复记录的身份损坏时，只在指纹仍匹配原任务的条件下替换为正确身份的系统错误。
     * @param task 原任务快照
     * @param result 正确身份的系统错误终态
     * @throws Exception 序列化或存储失败
     */
    public void repairCompleted(JudgeTask task, JudgeResult result) throws Exception {
        Long repaired = redis.execute(REPAIR,
                List.of(key(task.getExecutionId(), "state")),
                digest(task),
                mapper.writeValueAsString(result),
                String.valueOf(properties.getRetentionMillis()));
        if (!Long.valueOf(1).equals(repaired)) {
            throw new IllegalStateException("Worker 已完成结果修复条件不匹配");
        }
    }

    /**
     * 停止续租与截止回调线程。
     */
    @PreDestroy
    public void stop() {
        heartbeat.shutdownNow();
        deadlines.shutdownNow();
    }

    /**
     * 为同一执行生成位于相同 Redis Cluster 槽的键。
     * @param executionId 执行标识
     * @param suffix 内部固定状态后缀
     * @return 包含执行 hash tag 的完整键
     */
    private String key(String executionId, String suffix) {
        // 同一执行的 key 使用 Redis Cluster hash tag，可在同一个脚本内操作。
        return properties.getRedisPrefix() + "{" + executionId + "}:" + suffix;
    }

    /**
     * 领取判题任务后得到的租约或已完成结果
     */
    @Value
    public static class Claim {

        /**
         * 本次领取的执行租约
         */
        @ToString.Exclude
        Lease lease;

        /**
         * 可重发的既有终态结果
         */
        @ToString.Exclude
        JudgeResult completed;

        /**
         * 该任务是否已由其他 Worker 持有
         */
        boolean busy;

        /**
         * 领取时核对的任务指纹
         */
        String digest;
    }

    public final class Lease implements AutoCloseable {

        private final String state;
        private final String lock;
        private final String owner;
        private final int attempt;
        private final Long submissionId;
        private final String executionId;
        private final AtomicBoolean lost = new AtomicBoolean();
        private final Object lifecycle = new Object();
        private final ScheduledFuture<?> renewal;
        private final ScheduledFuture<?> deadline;
        private boolean closed;

        /**
         * 绑定当前执行线程并启动续租和总截止回调。
         * @param task 固定任务快照
         * @param state 状态记录键
         * @param lock 租约键
         * @param owner 本次租约唯一令牌
         * @param attempt 从 1 开始的执行次数
         */
        private Lease(JudgeTask task, String state, String lock, String owner, int attempt) {
            this.state = state;
            this.lock = lock;
            this.owner = owner;
            this.attempt = attempt;
            this.submissionId = task.getSubmissionId();
            this.executionId = task.getExecutionId();
            Thread running = Thread.currentThread();
            this.renewal = heartbeat.scheduleAtFixedRate(() -> {
                boolean owned;
                try {
                    owned = Long.valueOf(1).equals(redis.execute(RENEW,
                            List.of(lock), owner, String.valueOf(properties.getLeaseMillis())));
                } catch (RuntimeException e) {
                    owned = false;
                    log.warn("Worker 执行租约续期失败 submission={} execution={} attempt={} exception={}",
                            submissionId, executionId, attempt, e.getClass().getSimpleName());
                }
                // Redis 调用可能在 close 后才返回，关闭后不能再中断复用该线程的下一任务。
                synchronized (lifecycle) {
                    if (closed) {
                        return;
                    }
                    if (!owned && lost.compareAndSet(false, true)) {
                        log.warn("Worker 失去执行租约 submission={} execution={} attempt={}",
                                submissionId, executionId, attempt);
                        running.interrupt();
                    }
                }
            }, properties.getLeaseMillis() / 3, properties.getLeaseMillis() / 3, TimeUnit.MILLISECONDS);
            // 总截止时间独立于续租间隔与 Redis 响应时间，阻塞的数据准备也应及时中断。
            this.deadline = deadlines.schedule(() -> {
                synchronized (lifecycle) {
                    if (!closed) {
                        log.warn("Worker 执行达到总截止时间 submission={} execution={} attempt={}",
                                submissionId, executionId, attempt);
                        running.interrupt();
                    }
                }
            }, Math.max(0, task.getDeadlineEpochMs() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        }

        /**
         * 读取本次成功领取对应的执行次数。
         * @return 从 1 开始的执行次数
         */
        public int attempt() {
            return attempt;
        }

        /**
         * 续期并确认租约仍属于当前执行，否则禁止写终态。
         */
        public void requireOwned() {
            if (lost.get() || !Long.valueOf(1).equals(redis.execute(RENEW, List.of(lock), owner,
                    String.valueOf(properties.getLeaseMillis())))) {
                throw new IllegalStateException("Worker 已失去任务执行租约");
            }
        }

        /**
         * 确认租约后原子保存终态并释放占用。
         * @param result 合法终态结果
         * @throws Exception 序列化或存储失败
         */
        public void complete(JudgeResult result) throws Exception {
            requireOwned();
            stopRenewal();
            if (!Long.valueOf(1).equals(redis.execute(COMPLETE, List.of(state, lock), owner,
                    mapper.writeValueAsString(result), String.valueOf(properties.getRetentionMillis())))) {
                throw new IllegalStateException("Worker 已失去任务执行租约");
            }
        }

        /**
         * 停止回调并条件释放自身租约，失败等待自然到期。
         */
        @Override
        public void close() {
            stopRenewal();
            try {
                redis.execute(RELEASE, List.of(lock), owner);
            } catch (RuntimeException e) {
                // 结果确认及 ACK 已完成时，清理失败不能触发第二次 ACK 或重复发布；租约会自动过期。
                log.warn("Worker 租约释放失败，等待租约过期 submission={} execution={} attempt={} exception={}",
                        submissionId, executionId, attempt, e.getClass().getSimpleName());
            }
        }

        /**
         * 取消回调并标记关闭，防止中断线程复用后的下一任务。
         */
        private void stopRenewal() {
            synchronized (lifecycle) {
                closed = true;
                renewal.cancel(false);
                deadline.cancel(false);
            }
        }
    }
}
