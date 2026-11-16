package com.ymcloud.web.service.admin.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.bo.CreateStoredFileCommand;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminFileService;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import com.ymcloud.web.service.file.FileService;
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
    private FileService storedFiles;

    @Autowired
    private FileAccessPolicy access;

    @Autowired
    private ResourceAccessService resourceAccess;

    /**
     * 在分页及总数查询前过滤功能权限和业务资源归属
     * @param query 已校验的文件筛选条件
     * @return 已授权文件列表
     */
    @Override
    public PageVO<AdminFileVO> queryList(AdminFilePageQuery query) {
        FileAccessScope scope = new FileAccessScope();
        scope.setUserId(SecurityUtils.getUserId());
        scope.setAdministrator(SecurityUtils.isAdmin());
        scope.setProblemManager(resourceAccess.isProblemManager());
        scope.setContestManager(resourceAccess.isContestManager());
        scope.setGeneralFiles(access.canListGeneralFiles());
        scope.setPrivateFiles(access.canAccessPrivateFiles());
        scope.setTestData(access.canAccessTestData());
        if (!scope.isGeneralFiles() && !scope.isTestData()) {
            throw new AccessDeniedException("访问权限不足");
        }
        resolveBizType(query.getBizType());
        Page<File> page = Page.of(query.getPageNum(), query.getPageSize());
        Page<File> result = files.pageAuthorized(page, query, scope);
        return PageVO.of(result, this::metadata);
    }

    /**
     * 校验文件下载权限并返回存储元数据
     * @param id 对外 UUIDv4 文件标识
     * @return 已授权下载的文件
     */
    @Override
    public File requireDownload(String id) {
        File file = storedFiles.getByPublicId(id);
        access.requireDownload(file);
        return file;
    }

    /**
     * 根据文件权限上传通用文件，不接受题目测试数据关联
     * @param upload 上传内容
     * @param isPublic 文件公开状态
     * @return 已保存文件的展示信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminFileVO upload(MultipartFile upload, Integer isPublic) {
        if (!Integer.valueOf(FileConstants.ACCESS_PRIVATE).equals(isPublic)
                && !Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(isPublic)) {
            throw new FileException("公开状态必须为0或1", HttpStatus.BAD_REQUEST);
        }
        boolean publicAccess = Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(isPublic);
        access.requireUpload(publicAccess);
        User user = SecurityUtils.getUser();
        File file;
        try (InputStream input = upload.getInputStream()) {
            file = storedFiles.create(new CreateStoredFileCommand(
                    upload.getOriginalFilename(), upload.getContentType(), FileConstants.SUB_DIR_FILE,
                    FileBizType.GENERAL, null, publicAccess, user.getId(), user.getUsername()), input, upload.getSize());
        } catch (IOException e) {
            throw new FileException("读取上传文件失败", e);
        }
        return metadata(file);
    }

    /**
     * 锁定文件并校验删除权限，实际文件由存储服务在事务提交后删除
     * @param id 对外 UUIDv4 文件标识
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        // 与存储层删除和迁移使用相同的行锁，鉴权后关联信息不会并发变化
        File file = files.lambdaQuery()
                .eq(File::getPublicId, id)
                .last("FOR UPDATE")
                .one();
        if (file == null) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        access.requireDelete(file);
        storedFiles.delete(file.getId());
    }

    /**
     * 解析可选的业务类型筛选条件
     * @param code 业务类型编码，未筛选时为空
     * @return 对应业务类型，未筛选时为空
     */
    private FileBizType resolveBizType(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(FileBizType.values())
                .filter(type -> type.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new FileException("文件业务类型不合法", HttpStatus.BAD_REQUEST));
    }

    /**
     * 组装已通过列表资源过滤或上传归属验证的文件元数据
     * @param file 已授权文件
     * @return 文件展示信息
     */
    private AdminFileVO metadata(File file) {
        AdminFileVO view = new AdminFileVO();
        view.setId(file.getPublicId());
        view.setFileName(file.getFileName());
        view.setFileType(file.getFileType());
        view.setFileSize(file.getFileSize());
        view.setBizType(file.getBizType() == null ? null : file.getBizType().getCode());
        view.setBizId(file.getBizId());
        view.setIsPublic(access.isPublic(file) ? FileConstants.ACCESS_PUBLIC : FileConstants.ACCESS_PRIVATE);
        view.setStorageKey(file.getStorageKey());
        view.setUploaderUsername(file.getUploaderUsername());
        view.setCreatedTime(file.getCreatedTime());
        view.setUrl(access.isPublic(file) ? FileConstants.PUBLIC_URL_PREFIX + file.getPublicId() : null);
        view.setSha256(file.getSha256());
        view.setCanDownload(access.canDownloadAuthorizedFile(file));
        view.setCanDelete(access.canDelete(file));
        return view;
    }
}
