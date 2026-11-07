package com.ymcloud.judge;

import com.ymcloud.judge.listeners.WorkerTaskValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 验证任务数据、期限与本站语言的消息入口约束
 */
class WorkerTaskValidatorTest {

    /**
     * 正式任务需要数据摘要，过期任务保留终态恢复入口
     */
    @Test
    void validatesManifestAndDeadlineAndKeepsExpiredTaskForTerminalRecovery() {
        WorkerTaskValidator validator = new WorkerTaskValidator();
        var task = WorkerFixtures.task(0, 0);
        assertDoesNotThrow(() -> validator.validate(task));
        task.setManifestSha256(null);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task));
        var expired = WorkerFixtures.task(1, 0);
        expired.setSubmittedAtEpochMs(System.currentTimeMillis() - 2000L);
        expired.setDeadlineEpochMs(System.currentTimeMillis() - 1000L);
        assertDoesNotThrow(() -> validator.validate(expired));
    }

    /**
     * 任务语言必须映射到 Worker 支持的固定工具链
     */
    @Test
    void onlySupportedLanguagesCanReachExecutor() {
        WorkerTaskValidator validator = new WorkerTaskValidator();
        var task = WorkerFixtures.task(0, 0);
        assertDoesNotThrow(() -> validator.validate(task));
        task.getLanguageConfig().setName("Shell");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task));
    }

    /**
     * 题目资源可大于当前部署容量，由执行器报告明确的配置不足终态
     */
    @Test
    void capacityAboveDeploymentStillReachesRunnerForExplicitTerminalResult() {
        WorkerTaskValidator validator = new WorkerTaskValidator();
        var task = WorkerFixtures.task(1, 0);
        task.setMemoryLimit(1024 * 1024L);
        assertDoesNotThrow(() -> validator.validate(task));
    }
}
