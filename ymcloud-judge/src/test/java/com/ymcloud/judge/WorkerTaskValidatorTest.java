package com.ymcloud.judge;

import com.ymcloud.judge.listeners.WorkerTaskValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerTaskValidatorTest {

    @Test
    void validatesVersionAndDeadlineAndKeepsExpiredTaskForTerminalRecovery() {
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

    @Test
    void historicalImageAndCommandsAreCompatibilityMetadata() {
        WorkerTaskValidator validator = new WorkerTaskValidator();
        var task = WorkerFixtures.task(0, 0);
        task.getLanguageConfig().setEnv("obsolete-private-image");
        task.getLanguageConfig().setImage(null);
        task.getLanguageConfig().setCompileCmd(null);
        task.getLanguageConfig().setRunCmd(null);
        assertDoesNotThrow(() -> validator.validate(task));
        task.getLanguageConfig().setName("Shell");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task));
    }

    @Test
    void capacityAboveDeploymentStillReachesRunnerForExplicitTerminalResult() {
        WorkerTaskValidator validator = new WorkerTaskValidator();
        var task = WorkerFixtures.task(1, 0);
        task.setMemoryLimit(1024 * 1024L);
        assertDoesNotThrow(() -> validator.validate(task));
    }
}
