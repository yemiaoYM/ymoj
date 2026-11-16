package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.vo.common.StorageConfigVO;
import com.ymcloud.web.service.admin.AdminStorageConfigService;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 管理存储配置及连接测试，不记录存储凭据
 */
@RestController
@RequestMapping("/admin/storage/config")
public class AdminStorageConfigController {

    @Autowired
    private AdminStorageConfigService configs;

    /**
     * 查询存储配置，响应不包含明文或密文凭据。
     * @return 可管理的存储配置列表
     */
    @PreAuthorize("@ss.hasPermi('file:storage:list')")
    @GetMapping
    public Result<List<StorageConfigVO>> list() {
        return Result.ok(configs.list());
    }

    /**
     * 创建存储配置，服务另行检查超级管理员身份。
     * @param dto 配置参数，包含不能写入日志的凭据
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:add')")
    @PostMapping
    public Result<Void> create(@RequestBody StorageConfigDTO dto) {
        configs.create(dto);
        return Result.ok();
    }

    /**
     * 修改指定存储配置，保留已有文件的定位约束。
     * @param key 不可改名的配置标识
     * @param dto 配置参数，空凭据按服务规则保留旧值
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:edit')")
    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable("key") String key, @RequestBody StorageConfigDTO dto) {
        configs.update(key, dto);
        return Result.ok();
    }

    /**
     * 删除未使用且未被引用的数据库存储配置。
     * @param key 配置标识
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:remove')")
    @DeleteMapping("/{key}")
    public Result<Void> delete(@PathVariable("key") String key) {
        configs.delete(key);
        return Result.ok();
    }

    /**
     * 测试指定存储连接并记录通过测试的配置版本。
     * @param key 配置标识
     * @return 操作结果；测试失败由异常边界处理
     */
    @PreAuthorize("@ss.hasPermi('file:storage:test')")
    @PostMapping("/{key}/test")
    public Result<Void> test(@PathVariable("key") String key) {
        configs.test(key);
        return Result.ok();
    }

    /**
     * 启用已通过测试的当前配置版本。
     * @param key 配置标识
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:activate')")
    @PostMapping("/{key}/activate")
    public Result<Void> activate(@PathVariable("key") String key) {
        configs.activate(key);
        return Result.ok();
    }
}
