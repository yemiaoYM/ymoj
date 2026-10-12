package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.ResultJournal;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultJournalTest {

    @TempDir
    Path directory;

    @Test
    void restartRecoversOnlyMatchingTerminalResultWithoutTaskOrCredentials() throws Exception {
        WorkerProperties properties = new WorkerProperties();
        properties.setJournalDir(directory);
        var mapper = new WorkerAmqpConfig().workerObjectMapper();
        ResultJournal first = new ResultJournal(mapper, properties);
        String executionId = UUID.randomUUID().toString();
        assertNull(first.read(executionId, "digest"));
        JudgeResult result = JudgeResult.builder().executionId(executionId).terminal(true).sequence(3)
                .status(JudgeVerdict.STATUS_ACCEPTED).build();
        first.save(executionId, "digest", result);
        ResultJournal restarted = new ResultJournal(mapper, properties);
        assertEquals(result, restarted.read(executionId, "digest"));
        assertThrows(java.io.IOException.class, () -> restarted.read(executionId, "different"));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void rejectsPathsAndCorruptedResultRatherThanExecutingWithOldState() throws Exception {
        WorkerProperties properties = new WorkerProperties();
        properties.setJournalDir(directory);
        ResultJournal journal = new ResultJournal(new WorkerAmqpConfig().workerObjectMapper(), properties);
        assertThrows(IllegalArgumentException.class, () -> journal.read("../secret", "digest"));
        String executionId = UUID.randomUUID().toString();
        Files.writeString(directory.resolve(executionId + ".json"), "{corrupt}");
        assertThrows(java.io.IOException.class, () -> journal.read(executionId, "digest"));
        Files.writeString(directory.resolve(executionId + ".json"), "null");
        assertThrows(java.io.IOException.class, () -> journal.read(executionId, "digest"));
    }

    @Test
    void retentionPrunesOnlyExpiredOwnedResultsAndLeavesRecentOrUnrelatedFiles() throws Exception {
        WorkerProperties properties = new WorkerProperties();
        properties.setJournalDir(directory);
        properties.setRetentionMillis(60000L);
        ResultJournal journal = new ResultJournal(new WorkerAmqpConfig().workerObjectMapper(), properties);
        Path old = directory.resolve(UUID.randomUUID() + ".json");
        Path recent = directory.resolve(UUID.randomUUID() + ".json");
        Path unrelated = directory.resolve("keep.txt");
        Files.writeString(old, "{}");
        Files.writeString(recent, "{}");
        Files.writeString(unrelated, "preserve");
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 120000));
        journal.prune();
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(old));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(recent));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(unrelated));
    }
}
