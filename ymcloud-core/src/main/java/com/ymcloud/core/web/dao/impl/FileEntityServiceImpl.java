package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import org.springframework.stereotype.Service;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:41
 * @Description: FileEntityServiceImpl
 */
@Service
public class FileEntityServiceImpl extends ServiceImpl<FileMapper, File>
        implements FileEntityService {

    /**
     * 在分页和总数查询前过滤文件功能权限及资源归属
     * @param page 分页参数
     * @param query 文件筛选条件
     * @param scope 服务端计算的功能及资源权限
     * @return 已授权文件分页
     */
    @Override
    public Page<File> pageAuthorized(Page<File> page, AdminFilePageQuery query, FileAccessScope scope) {
        return baseMapper.pageAuthorized(page, query, scope);
    }
}
