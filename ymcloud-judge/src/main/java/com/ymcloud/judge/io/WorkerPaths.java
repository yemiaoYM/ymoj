package com.ymcloud.judge.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Worker 自有路径不允许通过任一级符号链接访问其他目录。
 */
public final class WorkerPaths {

    private WorkerPaths() {
    }

    public static void rejectSymbolicLinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Worker 工作路径不能包含符号链接");
            }
        }
    }

    /**
     * 校验代码目录挂载点，禁止覆盖容器根目录或使用相对路径。
     */
    public static String containerWorkingDirectory(String directory) {
        Objects.requireNonNull(directory, "容器工作目录不能为空");
        if (!directory.startsWith("/") || directory.contains("\\") || directory.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("容器工作目录必须为绝对 Linux 路径");
        }
        for (String segment : directory.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("容器工作目录不能包含相对路径");
            }
        }
        String normalized = directory.replaceAll("/+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("容器工作目录不能为根目录");
        }
        return normalized;
    }
}
