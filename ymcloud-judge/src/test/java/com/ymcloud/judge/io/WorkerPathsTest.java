package com.ymcloud.judge.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-25
 * @Description: 验证 Worker 文件路径不能经 Windows 目录联接进入其他目录。
 */
class WorkerPathsTest {

    @TempDir
    Path directory;

    /**
     * 普通目录下尚未创建的工作文件应允许由 Worker 创建。
     */
    @Test
    void ordinaryMissingPathIsAllowed() {
        assertDoesNotThrow(() -> WorkerPaths.rejectSymbolicLinks(directory.resolve("new/results.json")));
    }

    /**
     * 联接自身和联接下尚未创建的文件都必须被拒绝，不访问联接目标。
     * @throws Exception 创建或移除测试目录联接失败
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void junctionAndItsMissingChildAreRejected() throws Exception {
        Path target = Files.createDirectory(directory.resolve("outside"));
        Path marker = Files.writeString(target.resolve("keep.txt"), "preserve", StandardCharsets.UTF_8);
        Path junction = directory.resolve("junction");
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                junction.toString(), target.toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        try {
            assertThrows(IOException.class, () -> WorkerPaths.rejectSymbolicLinks(junction));
            assertThrows(IOException.class,
                    () -> WorkerPaths.rejectSymbolicLinks(junction.resolve("new-result.json")));
            assertEquals("preserve", Files.readString(marker, StandardCharsets.UTF_8));
        } finally {
            // 仅删除联接目录项；不递归删除目标，临时根目录由 JUnit 管理。
            Files.deleteIfExists(junction);
        }
    }
}
