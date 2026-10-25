package com.ymcloud.judge.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 拒绝 Worker 自有路径中的符号链接和 Windows 目录联接。
 */
public final class WorkerPaths {

    /**
     * 路径工具不创建实例。
     */
    private WorkerPaths() {}

    /**
     * 逐级检查已有父目录，尚未创建的路径也不能借父目录联接越界。
     * @param path Worker 自有目录或文件路径
     * @throws IOException 任一级为链接、目录联接或无法解析
     */
    public static void rejectSymbolicLinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Worker 工作路径不能包含符号链接");
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("Worker 工作路径不能包含目录联接");
            }
        }
    }
}
