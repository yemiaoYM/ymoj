package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.WorkerTaskValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerTaskValidatorTest {

    @Test
    void validatesVersionHashDeadlineAndImagesAndKeepsExpiredTaskForTerminalRecovery() {
        WorkerTaskValidator validator = new WorkerTaskValidator(new WorkerProperties());
        var task = WorkerFixtures.task(0, 0);
        assertDoesNotThrow(() -> validator.validate(task));
        task.setManifestSha256(null);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task));
        var invalidImage = WorkerFixtures.task(0, 0);
        invalidImage.getLanguageConfig().setEnv("host-privileged-image");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidImage));
        var expired = WorkerFixtures.task(1, 0);
        expired.setSubmittedAtEpochMs(System.currentTimeMillis() - 2000L);
        expired.setDeadlineEpochMs(System.currentTimeMillis() - 1000L);
        assertDoesNotThrow(() -> validator.validate(expired));
    }
}
