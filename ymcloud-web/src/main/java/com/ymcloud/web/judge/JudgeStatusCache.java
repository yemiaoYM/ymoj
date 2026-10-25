package com.ymcloud.web.judge;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: Redis 失败不影响已经提交的任务或结果，缓存按执行标识隔离。
 */
@Slf4j
@Component
public class JudgeStatusCache {

    @Autowired
    private RedisCache redisCache;

    /**
     * 按执行标识读取短期状态缓存，Redis 失败返回 null 供数据库恢复。
     * @param submissionId 提交或自测 ID
     * @param executionId 当前执行标识
     * @return 缓存状态；未命中或 Redis 失败时为 null
     */
    public JudgeResultVO get(Long submissionId, String executionId) {
        try {
            return redisCache.getCacheObject(key(submissionId, executionId));
        } catch (RuntimeException e) {
            log.debug("判题缓存读取失败，使用数据库状态，提交编号：{}", submissionId);
            return null;
        }
    }

    /**
     * 事务提交后写入状态；无事务的查询回填立即执行。
     * @param submissionId 提交或自测 ID
     * @param executionId 当前执行标识
     * @param result 已授权且允许缓存的状态视图
     */
    public void putAfterCommit(Long submissionId, String executionId, JudgeResultVO result) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 事务成功提交后执行状态缓存写入。
                 */
                @Override
                public void afterCommit() {
                    put(submissionId, executionId, result);
                }
            });
        } else {
            put(submissionId, executionId, result);
        }
    }

    /**
     * 写入有期限的执行状态，缓存故障不回滚已经保存的业务结果。
     * @param submissionId 提交或自测 ID
     * @param executionId 当前执行标识
     * @param result 允许缓存的状态视图
     */
    private void put(Long submissionId, String executionId, JudgeResultVO result) {
        try {
            redisCache.setCacheObject(key(submissionId, executionId), result, 30, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.debug("判题缓存写入失败，数据库结果已保留，提交编号：{}", submissionId);
        }
    }

    /**
     * 将提交和执行标识共同纳入缓存键，避免旧任务覆盖新执行。
     * @param submissionId 提交或自测 ID
     * @param executionId 当前执行标识
     * @return 带已有缓存前缀的完整键
     */
    private String key(Long submissionId, String executionId) {
        return CacheConstants.JUDGE_KEY + submissionId + ":" + executionId;
    }
}
