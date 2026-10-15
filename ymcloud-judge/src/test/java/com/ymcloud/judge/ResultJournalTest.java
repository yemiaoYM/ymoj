package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerAmqpConfig;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.listeners.ResultJournal;
import com.ymcloud.pojo.contract.judge.JudgeResult;
import com.ymcloud.pojo.contract.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

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

    @Test
    void staleTemporaryResultsArePrunedWithoutRemovingRecentOrUnrelatedFiles() throws Exception {
        WorkerProperties properties = new WorkerProperties();
        properties.setJournalDir(directory);
        var mapper = new WorkerAmqpConfig().workerObjectMapper();
        ResultJournal journal = new ResultJournal(mapper, properties);
        Path stale = Files.createTempFile(directory, ".result-", ".tmp");
        Path recent = Files.createTempFile(directory, ".result-", ".tmp");
        Path unrelated = Files.writeString(directory.resolve(".result-keep.tmp"), "preserve");
        Path folder = Files.createDirectory(directory.resolve(".result-123.tmp"));
        FileTime old = FileTime.fromMillis(System.currentTimeMillis() - 2 * 60 * 60 * 1000L);
        Files.setLastModifiedTime(stale, old);
        Files.setLastModifiedTime(unrelated, old);
        Files.setLastModifiedTime(folder, old);

        journal.prune();

        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.isDirectory(folder));
        Path abandonedAtRestart = Files.createTempFile(directory, ".result-", ".tmp");
        Files.setLastModifiedTime(abandonedAtRestart, old);
        new ResultJournal(mapper, properties);
        assertFalse(Files.exists(abandonedAtRestart));
        assertTrue(Files.exists(recent));
    }

    @Test
    void pruningWaitsForActiveWriteEvenWhenTemporaryFileIsOlderThanSafetyWindow() throws Exception {
        WorkerProperties properties = new WorkerProperties();
        properties.setJournalDir(directory);
        var mapper = spy(new WorkerAmqpConfig().workerObjectMapper());
        ResultJournal journal = new ResultJournal(mapper, properties);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch continueWriting = new CountDownLatch(1);
        CountDownLatch pruning = new CountDownLatch(1);
        AtomicReference<Path> temporary = new AtomicReference<>();
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            temporary.set(file.toPath());
            Files.setLastModifiedTime(file.toPath(),
                    FileTime.fromMillis(System.currentTimeMillis() - 2 * 60 * 60 * 1000L));
            writing.countDown();
            assertTrue(continueWriting.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(mapper).writeValue(any(File.class), any());
        String executionId = UUID.randomUUID().toString();
        JudgeResult result = JudgeResult.builder().executionId(executionId).terminal(true).sequence(3)
                .status(JudgeVerdict.STATUS_ACCEPTED).build();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var save = executor.submit(() -> {
                journal.save(executionId, "digest", result);
                return null;
            });
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            var prune = executor.submit(() -> {
                pruning.countDown();
                journal.prune();
                return null;
            });
            assertTrue(pruning.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> prune.get(100, TimeUnit.MILLISECONDS));
            assertTrue(Files.exists(temporary.get()));
            continueWriting.countDown();
            save.get(5, TimeUnit.SECONDS);
            prune.get(5, TimeUnit.SECONDS);
            assertEquals(result, journal.read(executionId, "digest"));
        } finally {
            continueWriting.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
