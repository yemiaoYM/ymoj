package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.pojo.entity.File;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 迁移默认仅核对，执行参数必须显式指定；不修改源文件字节。
 */
@RestController
@RequestMapping("/admin/storage/migration")
public class AdminStorageMigrationController {

    private final StoredFileService files;

    public AdminStorageMigrationController(StoredFileService files) {
        this.files = files;
    }

    @PreAuthorize("@ss.hasPermi('file:migration:query')")
    @GetMapping("/files")
    public Result<List<File>> inventory() {
        return Result.ok(files.legacyInventory());
    }

    @PreAuthorize("@ss.hasPermi('file:migration:execute')")
    @PostMapping("/files/{id}")
    public Result<File> migrate(@PathVariable("id") Long id,
                                @RequestParam(value = "apply", defaultValue = "false") boolean apply) {
        return Result.ok(files.migrateLocal(id, apply));
    }
}
