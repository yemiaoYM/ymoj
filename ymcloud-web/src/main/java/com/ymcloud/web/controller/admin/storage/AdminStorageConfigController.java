package com.ymcloud.web.controller.admin.storage;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.StorageConfigDTO;
import com.ymcloud.pojo.query.admin.StorageConfigPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.common.StorageConfigVO;
import com.ymcloud.web.service.file.StorageConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * @Description: 管理存储配置，不记录存储凭据
 */
@RestController
@RequestMapping("/admin/storage/config")
public class AdminStorageConfigController {

    @Autowired
    private StorageConfigService configs;

    /**
     * 查询存储配置，响应不包含明文或密文凭据。
     * @param query 配置筛选与分页参数
     * @return 可管理的存储配置分页
     */
    @PreAuthorize("@ss.hasPermi('file:storage:list')")
    @GetMapping("/list")
    public Result<PageVO<StorageConfigVO>> list(@Validated StorageConfigPageQuery query) {
        return Result.ok(configs.queryPageList(query));
    }

    /**
     * 按主键读取配置详情
     * @param id 配置主键
     * @return 不含访问凭据的配置详情
     */
    @PreAuthorize("@ss.hasPermi('file:storage:list')")
    @GetMapping("/{id}")
    public Result<StorageConfigVO> getInfo(@PathVariable("id") Long id) {
        return Result.ok(configs.queryById(id));
    }

    /**
     * 创建存储配置，服务另行检查超级管理员身份。
     * @param dto 配置参数，包含不能写入日志的凭据
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:add')")
    @PostMapping
    public Result<Void> create(@Validated @RequestBody StorageConfigDTO dto) {
        configs.insert(dto);
        return Result.ok();
    }

    /**
     * 修改指定存储配置，保留已有文件的定位约束。
     * @param dto 配置参数，空凭据按服务规则保留旧值
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:edit')")
    @PutMapping
    public Result<Void> update(@Validated @RequestBody StorageConfigDTO dto) {
        configs.update(dto);
        return Result.ok();
    }

    /**
     * 删除未使用且未被引用的数据库存储配置。
     * @param ids 配置主键集合
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:remove')")
    @DeleteMapping("/{ids}")
    public Result<Void> delete(@PathVariable("ids") List<Long> ids) {
        configs.deleteByIds(ids);
        return Result.ok();
    }

    /**
     * 按主键切换默认存储
     * @param dto 含目标配置主键的请求
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:storage:activate')")
    @PutMapping("/changeStatus")
    public Result<Void> changeStatus(@RequestBody StorageConfigDTO dto) {
        configs.changeStatus(dto.getId());
        return Result.ok();
    }
}
