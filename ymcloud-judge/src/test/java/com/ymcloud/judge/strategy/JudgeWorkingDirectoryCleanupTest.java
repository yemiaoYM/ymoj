package com.ymcloud.judge.strategy;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.judge.config.WorkerProperties;
import com.ymcloud.judge.data.TestDataCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

class JudgeWorkingDirectoryCleanupTest {

    @TempDir
    Path directory;

    @Test
    void cleansNestedExecutionArtifactsAndPreservesSiblingFiles() throws Exception {
        Path root = Files.createDirectories(directory.resolve("work"));
        Path execution = Files.createDirectory(root.resolve("execution-test"));
        Path sibling = Files.writeString(root.resolve("keep.txt"), "preserve");
        for (int folder = 0; folder < 10; folder++) {
            Path nested = Files.createDirectories(execution.resolve("nested-" + folder).resolve("deeper"));
            for (int file = 0; file < 50; file++) {
                Files.writeString(nested.resolve("artifact-" + file), "x");
            }
        }
        runner(root).deleteWorkingDirectory(execution);
        assertFalse(Files.exists(execution));
        assertEquals("preserve", Files.readString(sibling));
    }

    @Test
    void refusesWrongParentOrNonExecutionDirectory() throws Exception {
        Path root = Files.createDirectories(directory.resolve("work"));
        Path unrelated = Files.createDirectory(root.resolve("keep"));
        Path outside = Files.createDirectory(directory.resolve("execution-outside"));
        JudgeRun runner = runner(root);
        assertThrows(IOException.class, () -> runner.deleteWorkingDirectory(unrelated));
        assertThrows(IOException.class, () -> runner.deleteWorkingDirectory(outside));
        assertTrue(Files.isDirectory(unrelated));
        assertTrue(Files.isDirectory(outside));
    }

    @Test
    void removesLinksWithoutTraversingTheirExternalTarget() throws Exception {
        Path root = Files.createDirectories(directory.resolve("work"));
        Path execution = Files.createDirectory(root.resolve("execution-links"));
        Path outside = Files.createDirectories(directory.resolve("outside"));
        Path protectedFile = Files.writeString(outside.resolve("keep.txt"), "external data");
        try {
            Files.createSymbolicLink(execution.resolve("external-directory"), outside);
            Files.createSymbolicLink(execution.resolve("external-file"), protectedFile);
        } catch (IOException | UnsupportedOperationException deniedLinks) {
            assumeTrue(false, "当前平台不允许测试创建符号链接");
        }
        runner(root).deleteWorkingDirectory(execution);
        assertFalse(Files.exists(execution));
        assertEquals("external data", Files.readString(protectedFile));
        assertTrue(Files.isDirectory(outside));
    }

    private JudgeRun runner(Path root) {
        WorkerProperties properties = new WorkerProperties();
        properties.setWorkDir(root);
        return new JudgeRun(mock(DockerClient.class), mock(TestDataCache.class), properties);
    }
}
