package com.ymcloud.core.storage;

import com.ymcloud.common.exception.file.FileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地兼容路径检查，拒绝目录穿越和符号链接逃逸。
 */
public final class StoragePaths {

    private StoragePaths() {
    }

    public static Path resolve(Path root, String key) {
        if (key == null || key.isBlank() || key.contains("\\") || key.indexOf('\0') >= 0) {
            throw new FileException("非法文件路径");
        }
        Path relative = Path.of(key);
        if (relative.isAbsolute()) {
            throw new FileException("不允许绝对文件路径");
        }
        for (Path part : relative) {
            if (part.toString().equals("..") || part.toString().equals(".")) {
                throw new FileException("不允许目录穿越");
            }
        }
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            throw new FileException("文件路径超出存储目录");
        }
        try {
            Path ancestor = target;
            while (ancestor != null && !Files.exists(ancestor)) {
                if (Files.isSymbolicLink(ancestor)) {
                    throw new FileException("不允许符号链接文件");
                }
                ancestor = ancestor.getParent();
            }
            if (Files.exists(base) && ancestor != null
                    && !ancestor.toRealPath().startsWith(base.toRealPath())) {
                throw new FileException("文件路径超出存储目录");
            }
            for (Path cursor = target; cursor != null && cursor.startsWith(base); cursor = cursor.getParent()) {
                if (Files.isSymbolicLink(cursor)) {
                    throw new FileException("不允许符号链接文件");
                }
            }
            return target;
        } catch (IOException e) {
            throw new FileException("无法验证文件路径", e);
        }
    }
}
