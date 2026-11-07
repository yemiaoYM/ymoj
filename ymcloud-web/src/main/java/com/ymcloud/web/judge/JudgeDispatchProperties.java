package com.ymcloud.web.judge;

import com.ymcloud.pojo.contract.judge.JudgeProtocol;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 发布失败与结果超时有独立的有限预算，数据库任务不会无限重试。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ymcloud.judge.dispatch")
public class JudgeDispatchProperties {

    private int batchSize = 20;
    private int maxPublishAttempts = 5;
    private int maxRecoveryAttempts = 2;
    private Duration confirmTimeout = Duration.ofSeconds(5);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration retryInterval = Duration.ofSeconds(3);
    private Duration resultWait = Duration.ofMinutes(5);
    private Duration totalTimeout = Duration.ofMinutes(20);

    /**
     * 启动时验证发布、恢复、租约和截止预算，保持 Worker 与发布端的总截止约束一致
     */
    @PostConstruct
    public void validate() {
        if (batchSize < 1 || batchSize > 100 || maxPublishAttempts < 1 || maxPublishAttempts > 20
                || maxRecoveryAttempts < 0 || maxRecoveryAttempts > 10) {
            throw new IllegalArgumentException("判题任务发布和恢复次数配置超出允许范围");
        }
        Duration[] durations = {confirmTimeout, leaseDuration, retryInterval, resultWait, totalTimeout};
        for (Duration duration : durations) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("判题任务超时配置必须为正数");
            }
        }
        if (leaseDuration.compareTo(confirmTimeout.multipliedBy(2)) <= 0
                || totalTimeout.compareTo(resultWait) < 0
                || totalTimeout.compareTo(Duration.ofMillis(JudgeProtocol.MAX_TASK_DURATION_MILLIS)) > 0) {
            throw new IllegalArgumentException("判题租约或总超时无效，总超时不得超过一小时");
        }
        if (confirmTimeout.compareTo(Duration.ofSeconds(60)) > 0
                || leaseDuration.compareTo(Duration.ofMinutes(5)) > 0
                || retryInterval.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("发布确认不得超过60秒，发布租约不得超过5分钟，重试间隔不得超过1分钟");
        }
    }
}
