package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.vo.common.StorageConfigVO;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/storage/config")
public class AdminStorageConfigController {

    private final AdminStorageConfigService configs;

    public AdminStorageConfigController(AdminStorageConfigService configs) {
        this.configs = configs;
    }

    @PreAuthorize("@ss.hasPermi('file:storage:list')")
    @GetMapping
    public Result<List<StorageConfigVO>> list() {
        return Result.ok(configs.list());
    }

    @PreAuthorize("@ss.hasPermi('file:storage:add')")
    @PostMapping
    public Result<Void> create(@RequestBody StorageConfigDTO dto) {
        configs.create(dto);
        return Result.ok();
    }

    @PreAuthorize("@ss.hasPermi('file:storage:edit')")
    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable("key") String key, @RequestBody StorageConfigDTO dto) {
        configs.update(key, dto);
        return Result.ok();
    }

    @PreAuthorize("@ss.hasPermi('file:storage:remove')")
    @DeleteMapping("/{key}")
    public Result<Void> delete(@PathVariable("key") String key) {
        configs.delete(key);
        return Result.ok();
    }

    @PreAuthorize("@ss.hasPermi('file:storage:test')")
    @PostMapping("/{key}/test")
    public Result<Void> test(@PathVariable("key") String key) {
        configs.test(key);
        return Result.ok();
    }

    @PreAuthorize("@ss.hasPermi('file:storage:activate')")
    @PostMapping("/{key}/activate")
    public Result<Void> activate(@PathVariable("key") String key) {
        configs.activate(key);
        return Result.ok();
    }
}
