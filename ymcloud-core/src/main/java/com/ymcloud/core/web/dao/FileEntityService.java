package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: FileEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:40
 * @Description: FileEntityService
 */
public interface FileEntityService extends IService<File> {

    /**
     * 在分页和总数查询前过滤文件功能权限及资源归属
     * @param page 分页参数
     * @param query 文件筛选条件
     * @param scope 服务端计算的功能及资源权限
     * @return 已授权文件分页
     */
    Page<File> pageAuthorized(Page<File> page, AdminFilePageQuery query, FileAccessScope scope);
}
