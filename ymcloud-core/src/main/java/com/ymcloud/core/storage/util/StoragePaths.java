package com.ymcloud.core.storage.util;

import com.ymcloud.common.exception.file.FileException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 本地对象路径检查，拒绝目录穿越和符号链接逃逸
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StoragePaths {

    /**
     * 校验各存储驱动共用的相对对象键，不修补非法路径
     * @param key 使用正斜杠分隔且不超过 1024 个字符的对象键或命名空间
     * @throws FileException 包含空路径段、目录跳转、控制字符或平台路径语法
     */
    public static void validateObjectKey(String key) {
        if (key == null || key.isBlank() || key.length() > 1024 || key.contains("\\") || key.contains(":")) {
            throw new FileException("对象路径不合法");
        }
        for (String part : key.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")
                    || part.chars().anyMatch(character -> character < 32 || character == 127)) {
                throw new FileException("对象路径不合法");
            }
        }
    }

    /**
     * 将对象键解析到指定根目录内，并拒绝目录穿越和符号链接逃逸
     * @param root 允许访问的存储根目录
     * @param key 使用正斜杠分隔的相对对象键
     * @return 通过校验的绝对规范路径，目标可以尚不存在
     * @throws FileException 对象键非法、路径越界或无法验证真实路径
     */
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
