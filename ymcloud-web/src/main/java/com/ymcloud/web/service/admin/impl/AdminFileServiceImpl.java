package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.bo.FileUploadBO;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminFileService;
import com.ymcloud.web.service.admin.FileAccessPolicy;
import com.ymcloud.web.service.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件管理过滤权限后分页，通用入口不修改业务关联或不可变测试数据
 */
@Service
public class AdminFileServiceImpl implements AdminFileService {

    @Autowired
    private FileEntityService fileEntityService;

    @Autowired
    private FileService fileService;

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
        Page<FileInfo> page = Page.of(query.getPageNum(), query.getPageSize());
        Page<FileInfo> result = fileEntityService.pageAuthorized(page, query, scope);
        return PageVO.of(result, this::metadata);
    }

    /**
     * 按当前登录用户登记通用文件并返回管理页展示信息
     * @param file 管理员选择的文件
     * @return 已登记的文件信息
     */
    @Override
    public AdminFileVO upload(MultipartFile file) {
        FileUploadBO request = FileUploadBO.builder()
                .fileName(file.getOriginalFilename())
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .bizType(FileBizType.GENERAL)
                .uploaderId(SecurityUtils.getUserId())
                .build();
        try (InputStream input = file.getInputStream()) {
            return metadata(fileService.upload(request, input, file.getSize()));
        } catch (IOException e) {
            throw new FileException("读取上传文件失败", e);
        }
    }

    /**
     * 核对整组选中文件的管理权限后调用文件服务删除
     * @param fileIds 一个或多个文件主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> fileIds) {
        if (fileIds.isEmpty()) {
            throw new FileException("文件列表不能为空");
        }
        fileService.delete(fileIds);
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
    private AdminFileVO metadata(FileInfo file) {
        AdminFileVO view = BeanUtil.copyProperties(file, AdminFileVO.class);
        view.setCanDownload(access.canDownloadAuthorizedFile(file));
        view.setCanDelete(access.canDelete(file));
        return view;
    }
}
