package com.ymcloud.web.controller.admin.storage;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 通用文件管理，测试数据权限与删除生命周期由业务服务单独检查
 */
@RestController
@RequestMapping("/admin/file")
public class AdminFileController {

    @Autowired
    private AdminFileService service;

    /**
     * 按权限范围分页查询文件
     * @param query 分页及文件筛选条件
     * @return 当前用户可见的文件分页
     */
    @PreAuthorize("@ss.hasAnyPermi('file:file:list', 'problem:testcase:query')")
    @GetMapping("/list")
    public Result<PageVO<AdminFileVO>> list(@Validated AdminFilePageQuery query) {
        return Result.ok(service.queryList(query));
    }

    /**
     * 上传后台通用文件
     * @param file 管理员选择的文件
     * @return 已登记的文件信息
     */
    @PreAuthorize("@ss.hasPermi('file:file:upload')")
    @PostMapping("/upload")
    public Result<AdminFileVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(file));
    }

    /**
     * 按文件权限和业务关联约束删除文件
     * @param fileIds 一个或多个文件主键，多个主键以逗号分隔
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('file:file:remove')")
    @DeleteMapping("/{fileIds}")
    public Result<Void> delete(@PathVariable("fileIds") List<Long> fileIds) {
        service.delete(fileIds);
        return Result.ok();
    }

}
