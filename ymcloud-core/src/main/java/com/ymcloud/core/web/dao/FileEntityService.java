package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: FileEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:40
 * @Description: FileEntityService
 */
public interface FileEntityService extends IService<FileInfo> {

    /**
     * 在分页和总数查询前过滤文件功能权限及资源归属
     * @param page 分页参数
     * @param query 文件筛选条件
     * @param scope 服务端计算的功能及资源权限
     * @return 已授权文件分页
     */
    Page<FileInfo> pageAuthorized(Page<FileInfo> page, AdminFilePageQuery query, FileAccessScope scope);

    /**
     * 将当前用户预上传的正文图片关联到新建业务记录
     * @param fileIds 本次提交的图片主键
     * @param type 创建入口确定的文件业务类型
     * @param bizId 新建业务的数据库主键
     * @param userId 当前登录用户主键
     */
    void bindImages(List<Long> fileIds, FileBizType type, Long bizId, Long userId);
}
