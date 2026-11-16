package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.web.service.file.StoredFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 核对和迁移历史文件，默认只核对且不修改源文件字节
 */
@RestController
@RequestMapping("/admin/storage/migration")
public class AdminStorageMigrationController {

    @Autowired
    private StoredFileService files;

    /**
     * 查询等待核对或迁移的历史文件。
     * @return 历史文件清单
     */
    @PreAuthorize("@ss.hasPermi('file:migration:query')")
    @GetMapping("/files")
    public Result<List<File>> inventory() {
        return Result.ok(files.legacyInventory());
    }

    /**
     * 核对单个历史文件，显式指定 apply 才执行迁移。
     * @param id 文件编号
     * @param apply 是否实际执行；默认为 false，仅核对
     * @return 核对或迁移后的文件元数据
     */
    @PreAuthorize("@ss.hasPermi('file:migration:execute')")
    @PostMapping("/files/{id}")
    public Result<File> migrate(@PathVariable("id") Long id,
                                @RequestParam(value = "apply", defaultValue = "false") boolean apply) {
        return Result.ok(files.migrateLocal(id, apply));
    }
}
