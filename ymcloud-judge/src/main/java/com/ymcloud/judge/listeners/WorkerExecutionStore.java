package com.ymcloud.judge.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import jakarta.annotation.PreDestroy;
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
 * Redis 原子租约和任务指纹，只有当前租约持有者可以提交最终结果。
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
            log.debug("Worker 执行租约已被占用 submission={} execution={} version={}",
                    task.getSubmissionId(), task.getExecutionId(), task.getTestDataVersionId());
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
        log.debug("Worker 获取执行租约 submission={} execution={} version={} attempt={}",
                task.getSubmissionId(), task.getExecutionId(), task.getTestDataVersionId(), status - 10);
        return new Claim(new Lease(task, state, lock, owner, (int) (status - 10)), null, false, digest);
    }

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

    public String digest(JudgeTask task) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(task)));
    }

    /**
     * 已完成恢复记录的身份损坏时，只在指纹仍匹配原任务的条件下替换为正确身份的系统错误。
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

    @PreDestroy
    public void stop() {
        heartbeat.shutdownNow();
        deadlines.shutdownNow();
    }

    private String key(String executionId, String suffix) {
        // 同一执行的 key 使用 Redis Cluster hash tag，可在同一个脚本内操作。
        return properties.getRedisPrefix() + "{" + executionId + "}:" + suffix;
    }

    public record Claim(Lease lease, JudgeResult completed, boolean busy, String digest) {
    }

    public final class Lease implements AutoCloseable {

        private final String state;
        private final String lock;
        private final String owner;
        private final int attempt;
        private final Long submissionId;
        private final String executionId;
        private final Long versionId;
        private final AtomicBoolean lost = new AtomicBoolean();
        private final Object lifecycle = new Object();
        private final ScheduledFuture<?> renewal;
        private final ScheduledFuture<?> deadline;
        private boolean closed;

        private Lease(JudgeTask task, String state, String lock, String owner, int attempt) {
            this.state = state;
            this.lock = lock;
            this.owner = owner;
            this.attempt = attempt;
            this.submissionId = task.getSubmissionId();
            this.executionId = task.getExecutionId();
            this.versionId = task.getTestDataVersionId();
            Thread running = Thread.currentThread();
            this.renewal = heartbeat.scheduleAtFixedRate(() -> {
                boolean owned;
                try {
                    owned = Long.valueOf(1).equals(redis.execute(RENEW,
                            List.of(lock), owner, String.valueOf(properties.getLeaseMillis())));
                } catch (RuntimeException e) {
                    owned = false;
                    log.warn("Worker 执行租约续期失败 submission={} execution={} version={} attempt={} exception={}",
                            submissionId, executionId, versionId, attempt, e.getClass().getSimpleName());
                }
                // Redis 调用可能在 close 后才返回，关闭后不能再中断复用该线程的下一任务。
                synchronized (lifecycle) {
                    if (closed) {
                        return;
                    }
                    if (!owned && lost.compareAndSet(false, true)) {
                        log.warn("Worker 失去执行租约 submission={} execution={} version={} attempt={}",
                                submissionId, executionId, versionId, attempt);
                        running.interrupt();
                    }
                }
            }, properties.getLeaseMillis() / 3, properties.getLeaseMillis() / 3, TimeUnit.MILLISECONDS);
            // 总截止时间独立于续租间隔与 Redis 响应时间，阻塞的数据准备也应及时中断。
            this.deadline = deadlines.schedule(() -> {
                synchronized (lifecycle) {
                    if (!closed) {
                        log.warn("Worker 执行达到总截止时间 submission={} execution={} version={} attempt={}",
                                submissionId, executionId, versionId, attempt);
                        running.interrupt();
                    }
                }
            }, Math.max(0, task.getDeadlineEpochMs() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        }

        public int attempt() {
            return attempt;
        }

        public void requireOwned() {
            if (lost.get() || !Long.valueOf(1).equals(redis.execute(RENEW, List.of(lock), owner,
                    String.valueOf(properties.getLeaseMillis())))) {
                throw new IllegalStateException("Worker 已失去任务执行租约");
            }
        }

        public void complete(JudgeResult result) throws Exception {
            requireOwned();
            stopRenewal();
            if (!Long.valueOf(1).equals(redis.execute(COMPLETE, List.of(state, lock), owner,
                    mapper.writeValueAsString(result), String.valueOf(properties.getRetentionMillis())))) {
                throw new IllegalStateException("Worker 已失去任务执行租约");
            }
        }

        @Override
        public void close() {
            stopRenewal();
            try {
                redis.execute(RELEASE, List.of(lock), owner);
            } catch (RuntimeException e) {
                // 结果确认及 ACK 已完成时，清理失败不能触发第二次 ACK 或重复发布；租约会自动过期。
                log.warn("Worker 租约释放失败，等待租约过期 submission={} execution={} version={} attempt={} exception={}",
                        submissionId, executionId, versionId, attempt, e.getClass().getSimpleName());
            }
        }

        private void stopRenewal() {
            synchronized (lifecycle) {
                closed = true;
                renewal.cancel(false);
                deadline.cancel(false);
            }
        }
    }
}
