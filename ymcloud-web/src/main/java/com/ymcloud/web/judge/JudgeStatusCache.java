package com.ymcloud.web.judge;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

/** Redis 失败不影响已经提交的任务或结果，缓存按执行标识隔离。 */
@Slf4j
@Component
public class JudgeStatusCache {

    private final RedisCache redisCache;

    public JudgeStatusCache(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    public JudgeResultVO get(Long submissionId, String executionId) {
        try {
            return redisCache.getCacheObject(key(submissionId, executionId));
        } catch (RuntimeException e) {
            log.debug("判题缓存读取失败，使用数据库状态，提交编号：{}", submissionId);
            return null;
        }
    }

    public void putAfterCommit(Long submissionId, String executionId, JudgeResultVO result) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    put(submissionId, executionId, result);
                }
            });
        } else {
            put(submissionId, executionId, result);
        }
    }

    private void put(Long submissionId, String executionId, JudgeResultVO result) {
        try {
            redisCache.setCacheObject(key(submissionId, executionId), result, 30, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.debug("判题缓存写入失败，数据库结果已保留，提交编号：{}", submissionId);
        }
    }

    private String key(Long submissionId, String executionId) {
        return CacheConstants.JUDGE_KEY + submissionId + ":" + executionId;
    }
}
