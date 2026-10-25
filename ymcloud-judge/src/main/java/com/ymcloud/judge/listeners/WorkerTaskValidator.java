package com.ymcloud.judge.listeners;

import com.ymcloud.pojo.contract.judge.JudgeLanguage;
import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import com.ymcloud.pojo.contract.judge.JudgeTask;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 消费端重复校验消息边界，不信任类型标头或提交者可控字段。
 */
@Component
public class WorkerTaskValidator {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("Java", "C", "C++", "Python");
    private static final Pattern SOURCE_FILE_NAME = Pattern.compile("[A-Za-z0-9_-]+\\.(java|c|cpp|py)");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /**
     * 为独立消息入口建立任务校验边界。
     * @param task 不可信任务契约
     */
    public void validate(JudgeTask task) {
        validateIdentity(task);
        validateDeadline(task);
        validateExecutionLimits(task);
        validateLanguage(task.getLanguageConfig());
        validateData(task);
    }

    /**
     * 校验任务协议及执行身份。
     * @param task 待检查任务
     */
    private void validateIdentity(JudgeTask task) {
        if (task == null || task.getSchemaVersion() != JudgeProtocol.SCHEMA_VERSION
                || task.getExecutionId() == null
                || !UUID.fromString(task.getExecutionId()).toString().equals(task.getExecutionId())
                || task.getSubmissionId() == null || task.getSubmissionId() <= 0
                || task.getStrategy() == null || (task.getStrategy() != 0 && task.getStrategy() != 1)
                || task.getMode() == null || (task.getMode() != 0 && task.getMode() != 1)) {
            throw new IllegalArgumentException("判题任务身份或协议无效");
        }
    }

    /**
     * 校验创建时间、总截止及允许时长。
     * @param task 待检查任务
     */
    private void validateDeadline(JudgeTask task) {
        if (task.getSubmittedAtEpochMs() == null || task.getDeadlineEpochMs() == null
                || task.getSubmittedAtEpochMs() <= 0 || task.getDeadlineEpochMs() <= 0
                || task.getSubmittedAtEpochMs() > System.currentTimeMillis() + 60000L
                || task.getDeadlineEpochMs() <= task.getSubmittedAtEpochMs()
                || task.getDeadlineEpochMs() - task.getSubmittedAtEpochMs() > 60 * 60 * 1000L) {
            throw new IllegalArgumentException("判题任务截止时间无效");
        }
        // 已过期任务仍进入恢复分支，发送终态或死信，避免永久滞留。
    }

    /**
     * 校验语言执行资源和计分策略。
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
     * 只接受支持的本站语言与固定文件名。
     * @param language 待检查语言快照
     */
    private void validateLanguage(JudgeLanguage language) {
        if (language == null || language.getName() == null
                || language.getFileName() == null
                || !SOURCE_FILE_NAME.matcher(language.getFileName()).matches()
                || language.getType() == null || (language.getType() != 0 && language.getType() != 1)
                || !SUPPORTED_LANGUAGES.contains(language.getName())) {
            throw new IllegalArgumentException("判题语言配置无效");
        }
        // 历史快照的 env、compileCmd 和 runCmd 仅作元数据；执行命令由 Worker 固定。
    }

    /**
     * 区分有界自测输入与固定版本正式数据。
     * @param task 待检查任务
     */
    private void validateData(JudgeTask task) {
        if (task.getStrategy() == 0) {
            if (task.getProblemId() == null || task.getProblemId() <= 0
                    || task.getTestDataVersionId() == null || task.getTestDataVersionId() <= 0
                    || task.getManifestSha256() == null
                    || !SHA256.matcher(task.getManifestSha256()).matches()) {
                throw new IllegalArgumentException("正式评测必须固定数据版本和清单哈希");
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
