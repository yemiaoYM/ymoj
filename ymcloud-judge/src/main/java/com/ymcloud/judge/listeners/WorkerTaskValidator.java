package com.ymcloud.judge.listeners;

import com.ymcloud.judge.constant.JudgeConstants;
import com.ymcloud.pojo.bo.judge.JudgeTask;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 校验判题消息的执行身份、期限、资源与数据快照
 */
@Component
public class WorkerTaskValidator {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("Java", "C", "C++", "Python");

    /**
     * 为独立消息入口建立任务校验边界
     * @param task 不可信任务契约
     */
    public void validate(JudgeTask task) {
        validateIdentity(task);
        validateDeadline(task);
        validateExecutionLimits(task);
        validateLanguage(task.getLanguage());
        validateData(task);
        if (task.getSpj() != null) {
            if (task.getStrategy() != 0) {
                throw new IllegalArgumentException("自测任务不支持隐藏 SPJ");
            }
            task.getSpj().validate();
        }
    }

    /**
     * 校验任务的执行身份和计分策略
     * @param task 待检查任务
     */
    private void validateIdentity(JudgeTask task) {
        if (task == null || task.getExecutionId() == null
                || !UUID.fromString(task.getExecutionId()).toString().equals(task.getExecutionId())
                || task.getSubmissionId() == null || task.getSubmissionId() <= 0
                || task.getStrategy() == null || (task.getStrategy() != 0 && task.getStrategy() != 1)
                || task.getMode() == null || (task.getMode() != 0 && task.getMode() != 1)) {
            throw new IllegalArgumentException("判题任务身份或协议无效");
        }
    }

    /**
     * 校验创建时间、总截止及允许时长
     * @param task 待检查任务
     */
    private void validateDeadline(JudgeTask task) {
        if (task.getSubmittedAtEpochMs() == null || task.getDeadlineEpochMs() == null
                || task.getSubmittedAtEpochMs() <= 0 || task.getDeadlineEpochMs() <= 0
                || task.getSubmittedAtEpochMs() > System.currentTimeMillis() + JudgeConstants.MAX_CLOCK_SKEW_MILLIS
                || task.getDeadlineEpochMs() <= task.getSubmittedAtEpochMs()
                || task.getDeadlineEpochMs() - task.getSubmittedAtEpochMs() > JudgeConstants.MAX_TASK_DURATION_MILLIS) {
            throw new IllegalArgumentException("判题任务截止时间无效");
        }
        // 已过期任务仍进入恢复分支，发送终态或死信，避免永久滞留
    }

    /**
     * 校验源码大小及执行资源限制
     * @param task 待检查任务
     */
    private void validateExecutionLimits(JudgeTask task) {
        if (task.getCode() == null || task.getCode().length() > 256 * 1024
                || task.getTimeLimit() == null || task.getTimeLimit() < 1 || task.getTimeLimit() > 60000
                || task.getMemoryLimit() == null || task.getMemoryLimit() < 1024
                || task.getMemoryLimit() > 2 * 1024 * 1024L) {
            throw new IllegalArgumentException("判题代码或资源限制无效");
        }
    }

    /**
     * 只接受 Worker 工具链支持的本站语言
     * @param language 待检查语言快照
     */
    private void validateLanguage(String language) {
        if (language == null || !SUPPORTED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("判题语言配置无效");
        }
    }

    /**
     * 区分有界自测输入与正式评测的题目编号
     * @param task 待检查任务
     */
    private void validateData(JudgeTask task) {
        if (task.getStrategy() == 0) {
            if (task.getProblemId() == null || task.getProblemId() <= 0) {
                throw new IllegalArgumentException("正式评测缺少有效题目 ID");
            }
            return;
        }
        if (task.getInput() == null || task.getExpectedOutput() == null
                || task.getInput().length() > 1024 * 1024
                || task.getExpectedOutput().length() > 1024 * 1024) {
            throw new IllegalArgumentException("自测输入或输出无效或过大");
        }
    }
}
