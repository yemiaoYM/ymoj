package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:41
 * @Description: FileEntityServiceImpl
 */
@Service
public class FileEntityServiceImpl extends ServiceImpl<FileMapper, FileInfo>
        implements FileEntityService {

    /**
     * 在分页和总数查询前过滤文件功能权限及资源归属
     * @param page 分页参数
     * @param query 文件筛选条件
     * @param scope 服务端计算的功能及资源权限
     * @return 已授权文件分页
     */
    @Override
    public Page<FileInfo> pageAuthorized(Page<FileInfo> page, AdminFilePageQuery query, FileAccessScope scope) {
        return baseMapper.pageAuthorized(page, query, scope);
    }

    /**
     * 将当前用户预上传的正文图片关联到新建业务记录
     * @param fileIds 本次提交的图片主键
     * @param type 创建入口确定的文件业务类型
     * @param bizId 新建业务的数据库主键
     * @param userId 当前登录用户主键
     * @throws FileException 部分图片不存在、已绑定或不属于本次业务及用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindImages(List<Long> fileIds, FileBizType type, Long bizId, Long userId) {
        List<Long> ids = fileIds.stream().distinct().toList();
        if (baseMapper.bindImages(ids, type, bizId, userId) != ids.size()) {
            throw new FileException("部分图片已绑定或不可用，请重新选择", HttpStatus.CONFLICT);
        }
    }
}
