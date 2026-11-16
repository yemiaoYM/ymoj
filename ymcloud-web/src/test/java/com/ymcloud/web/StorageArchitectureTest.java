package com.ymcloud.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 防止存储基础设施重新引入 Web 和 OJ 业务依赖
 */
class StorageArchitectureTest {

    /**
     * 检查 Core 依赖方向与存储包的业务隔离边界
     * @throws Exception 源文件读取失败
     */
    @Test
    void coreStorageDoesNotDependOnWebOrJudgeBusiness() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("ymcloud-core"))) {
            root = root.getParent();
        }
        Path core = root.resolve("ymcloud-core/src/main/java");
        assertTrue(Files.isDirectory(core));
        List<String> businessTypes = List.of("FileBizType", "pojo.entity.Problem",
                "pojo.entity.Contest", "pojo.entity.User", "pojo.contract.judge",
                "TestDataService", "StoredFileService", "core.security");
        try (var paths = Files.walk(core)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                String declarations = source.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
                assertFalse(declarations.contains("com.ymcloud.web."), path.toString());
                if (path.startsWith(core.resolve("com/ymcloud/core/storage"))) {
                    for (String type : businessTypes) {
                        assertFalse(source.contains(type), path + " 引用了 " + type);
                    }
                }
            }
        }
        assertFalse(Files.exists(core.resolve("com/ymcloud/core/storage/StoredFileService.java")));
        assertFalse(Files.exists(core.resolve("com/ymcloud/core/storage/TestDataServiceImpl.java")));
    }
}
