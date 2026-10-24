package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminFileService;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件管理过滤权限后分页，通用入口不修改业务关联或不可变测试数据
 */
@Service
public class AdminFileServiceImpl implements AdminFileService {

    @Autowired
    private FileEntityService files;

    @Autowired
    private StoredFileService storedFiles;

    @Autowired
    private FileAccessPolicy access;

    @Override
    public PageVO<AdminFileVO> queryList(AdminFilePageQuery query) {
        boolean general = access.canListGeneralFiles();
        boolean privateFiles = access.canAccessPrivateFiles();
        boolean testData = access.canAccessTestData();
        if (!general && !testData) {
            throw new AccessDeniedException("访问权限不足");
        }
        FileBizType bizType = resolveBizType(query.getBizType());
        Page<File> page = Page.of(query.getPageNum(), query.getPageSize());
        LambdaQueryChainWrapper<File> chain = files.lambdaQuery()
                .like(StrUtil.isNotBlank(query.getFileName()), File::getFileName, query.getFileName())
                .eq(bizType != null, File::getBizType, bizType)
                .eq(StrUtil.isNotBlank(query.getStorageProfile()), File::getStorageProfile, query.getStorageProfile());
        if (Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(query.getIsPublic())) {
            chain.eq(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                    .and(business -> business.ne(File::getBizType, FileBizType.TEST_DATA)
                            .or()
                            .isNull(File::getBizType));
        } else if (Integer.valueOf(FileConstants.ACCESS_PRIVATE).equals(query.getIsPublic())) {
            chain.and(visibility -> visibility.ne(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                    .or()
                    .isNull(File::getIsPublic)
                    .or()
                    .eq(File::getBizType, FileBizType.TEST_DATA));
        }
        // 可见性条件在 SQL 分页和总数查询前执行，避免泄露私有记录或总数。
        if (!(general && privateFiles && testData)) {
            chain.and(visible -> {
                if (general) {
                    visible.nested(ordinary -> {
                        ordinary.and(business -> business.ne(File::getBizType, FileBizType.TEST_DATA)
                                .or()
                                .isNull(File::getBizType));
                        if (!privateFiles) {
                            ordinary.eq(File::getIsPublic, FileConstants.ACCESS_PUBLIC);
                        }
                    });
                }
                if (testData) {
                    if (general) {
                        visible.or();
                    }
                    visible.eq(File::getBizType, FileBizType.TEST_DATA);
                }
            });
        }
        Page<File> result = chain.orderByDesc(File::getId)
                .page(page);
        return PageVO.of(result, this::visibleMetadata);
    }

    @Override
    public File requireDownload(Long id) {
        File file = storedFiles.get(id);
        access.requireDownload(file);
        return file;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminFileVO upload(MultipartFile upload, Integer isPublic) {
        if (!Integer.valueOf(0).equals(isPublic) && !Integer.valueOf(1).equals(isPublic)) {
            throw new FileException("公开状态必须为0或1", HttpStatus.BAD_REQUEST);
        }
        boolean publicAccess = Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(isPublic);
        access.requireUpload(publicAccess);
        User user = SecurityUtils.getUser();
        File file = new File();
        file.setFileName(upload.getOriginalFilename());
        file.setFileType(upload.getContentType());
        file.setBizType(FileBizType.GENERAL);
        file.setIsPublic(isPublic);
        file.setUploaderId(user.getId());
        file.setUploaderUsername(user.getUsername());
        try (InputStream input = upload.getInputStream()) {
            storedFiles.save(input, upload.getSize(), file);
        } catch (IOException e) {
            throw new FileException("读取上传文件失败", e);
        }
        return metadata(file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 与存储层删除和迁移使用相同的行锁，鉴权后关联信息不会并发变化。
        File file = files.lambdaQuery()
                .eq(File::getId, id)
                .last("FOR UPDATE")
                .one();
        if (file == null) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        access.requireDelete(file);
        storedFiles.delete(id);
    }

    private FileBizType resolveBizType(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(FileBizType.values())
                .filter(type -> type.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new FileException("文件业务类型不合法", HttpStatus.BAD_REQUEST));
    }

    private AdminFileVO visibleMetadata(File file) {
        if (!access.canList(file)) {
            throw new AccessDeniedException("访问权限不足");
        }
        return metadata(file);
    }

    private AdminFileVO metadata(File file) {
        AdminFileVO view = new AdminFileVO();
        view.setId(file.getId());
        view.setFileName(file.getFileName());
        view.setFileType(file.getFileType());
        view.setFileSize(file.getFileSize());
        view.setBizType(file.getBizType() == null ? null : file.getBizType().getCode());
        view.setBizId(file.getBizId());
        view.setIsPublic(access.isPublic(file) ? FileConstants.ACCESS_PUBLIC : FileConstants.ACCESS_PRIVATE);
        view.setStorageProfile(file.getStorageProfile());
        view.setUploaderUsername(file.getUploaderUsername());
        view.setCreatedTime(file.getCreatedTime());
        view.setUrl(access.isPublic(file) ? file.getUrl() : null);
        view.setSha256(file.getSha256());
        view.setCanDownload(access.canDownload(file));
        view.setCanDelete(access.canDelete(file));
        return view;
    }
}
