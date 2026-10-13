package com.ymcloud.judge.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

}
