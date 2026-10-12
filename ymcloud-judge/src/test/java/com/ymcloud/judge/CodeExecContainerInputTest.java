package com.ymcloud.judge;

import com.github.dockerjava.api.DockerClient;
import com.ymcloud.judge.bo.CmdExecResult;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.exception.CommandTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 文件 stdin 保留原始字节与 EOF，shell 位置参数不执行输入或命令中的特殊字符。
 */
class CodeExecContainerInputTest {

    @TempDir
    private Path workingDir;

    @Test
    void utf8InputAndSpecialCharactersAreDataAndTemporaryFileIsDeleted() throws Exception {
        CodeExecContainer container = container("/work space/$dollar'quote");
        String input = " \t中文😀\r\n$(touch forbidden); ' \" \\ trailing  \n\n";
        String[] originalCommand = {"printf", "%s", "$(touch marker); ' \" \\"};
        Path sibling = Files.writeString(workingDir.resolve("keep.txt"), "preserve", StandardCharsets.UTF_8);
        List<Path> inputs = new ArrayList<>();
        doAnswer(invocation -> {
            String[] wrapped = invocation.getArgument(0);
            assertEquals("/bin/sh", wrapped[0]);
            assertEquals("-c", wrapped[1]);
            assertEquals("exec \"$@\" < \"$0\"", wrapped[2]);
            assertTrue(wrapped[3].startsWith("/work space/$dollar'quote/.judge-stdin-"));
            assertArrayEquals(originalCommand, java.util.Arrays.copyOfRange(wrapped, 4, wrapped.length));
            Path inputPath = inputPath(wrapped[3]);
            inputs.add(inputPath);
            assertArrayEquals(input.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(inputPath));
            return CmdExecResult.builder().exitCode(0L).stdout(input).stderr("").build();
        }).when(container).execCmd(any(String[].class), anyLong());

        assertEquals(input, container.execCmd(originalCommand, input).getStdout());

        assertEquals(1, inputs.size());
        assertFalse(Files.exists(inputs.get(0)));
        assertEquals("preserve", Files.readString(sibling, StandardCharsets.UTF_8));
        assertFalse(Files.exists(workingDir.resolve("forbidden")));
        assertFalse(Files.exists(workingDir.resolve("marker")));
    }

    @Test
    void emptyInputsHaveDistinctFilesAndAreRemovedAfterEachExecution() throws Exception {
        CodeExecContainer container = container("/app");
        List<Path> inputs = new ArrayList<>();
        doAnswer(invocation -> {
            String[] wrapped = invocation.getArgument(0);
            Path inputPath = inputPath(wrapped[3]);
            inputs.add(inputPath);
            assertEquals(0, Files.size(inputPath));
            return CmdExecResult.builder().exitCode(0L).stdout("").stderr("").build();
        }).when(container).execCmd(any(String[].class), anyLong());

        container.execCmd(new String[]{"cat"}, "");
        container.execCmd(new String[]{"cat"}, "");

        assertNotEquals(inputs.get(0), inputs.get(1));
        for (Path input : inputs) {
            assertFalse(Files.exists(input));
        }
    }

    @Test
    void executionFailureStillDeletesOnlyItsOwnTemporaryInput() throws Exception {
        CodeExecContainer container = container("/app");
        List<Path> inputs = new ArrayList<>();
        doAnswer(invocation -> {
            String[] wrapped = invocation.getArgument(0);
            inputs.add(inputPath(wrapped[3]));
            throw new IllegalStateException("Docker unavailable");
        }).when(container).execCmd(any(String[].class), anyLong());

        assertThrows(IllegalStateException.class, () -> container.execCmd(new String[]{"cat"}, "input"));

        assertEquals(1, inputs.size());
        assertFalse(Files.exists(inputs.get(0)));
    }

    @Test
    void invalidContainerPathsAndEmptyCommandFailBeforeCreatingInput() throws Exception {
        for (String directory : new String[]{"relative", "/", "/app/../escape", "/app/./child", "C:\\app"}) {
            assertThrows(IllegalArgumentException.class, () -> container(directory));
        }
        CodeExecContainer container = container("/app");
        assertThrows(IllegalArgumentException.class, () -> container.execCmd(new String[0], "input"));
        assertThrows(NullPointerException.class, () -> container.execCmd(new String[]{"cat"}, null));
        assertThrows(IllegalArgumentException.class, () -> container.execCmd(
                new String[]{"cat"}, InputStream.nullInputStream(), 0L));
        try (var files = Files.list(workingDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void slowInputPreparationCannotExtendCommandDeadlineOrStartExpiredCommand() throws Exception {
        CodeExecContainer container = container("/app");
        InputStream delayed = new ByteArrayInputStream("input".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.read(buffer, offset, length);
            }
        };
        assertThrows(CommandTimeoutException.class, () -> container.execCmd(new String[]{"cat"}, delayed, 1L));
        verify(container, never()).execCmd(any(String[].class), anyLong());
        try (var files = Files.list(workingDir)) {
            assertEquals(0, files.count());
        }
    }

    private CodeExecContainer container(String containerWorkingDir) {
        return spy(new CodeExecContainer(mock(DockerClient.class), "test-only", workingDir.toString(),
                containerWorkingDir));
    }

    private Path inputPath(String containerPath) {
        return workingDir.resolve(containerPath.substring(containerPath.lastIndexOf('/') + 1));
    }
}
