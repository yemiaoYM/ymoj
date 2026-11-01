package com.ymcloud.web.service.admin;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.FileBizType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件功能权限及题目、比赛归属边界，隐藏数据不通过通用文件入口扩大授权
 */
@Component
public class FileAccessPolicy {

    @Autowired
    private PermissionService permissions;

    @Autowired
    private ResourceAccessService resourceAccess;

    @Autowired
    private ProblemEntityService problems;

    @Autowired
    private ContestEntityService contests;

    /**
     * 判断通用文件列表功能权限
     * @return 是否可查询通用文件
     */
    public boolean canListGeneralFiles() {
        return permissions.hasPermi("file:file:list");
    }

    /**
     * 判断独立的私有文件功能权限
     * @return 是否可访问普通私有文件
     */
    public boolean canAccessPrivateFiles() {
        return permissions.hasPermi("file:file:private");
    }

    /**
     * 判断测试数据功能权限，具体原题仍需归属检查
     * @return 是否具有题目数据查询能力
     */
    public boolean canAccessTestData() {
        return permissions.hasPermi("problem:testcase:query") && resourceAccess.isProblemManager();
    }

    /**
     * 判断单条文件是否可进入列表
     * @param file 文件元数据
     * @return 是否允许列出
     */
    public boolean canList(File file) {
        if (file == null) {
            return false;
        }
        if (file.getBizType() == FileBizType.TEST_DATA) {
            return canAccessTestData() && canAccessResource(file);
        }
        return canListGeneralFiles() && (isPublic(file) || canAccessPrivateFiles()) && canAccessResource(file);
    }

    /**
     * 检查下载功能及所属资源
     * @param file 文件元数据
     * @return 是否允许下载
     */
    public boolean canDownload(File file) {
        return file != null && canDownloadAuthorizedFile(file) && canAccessResource(file);
    }

    /**
     * 为已经通过 SQL 资源过滤的列表计算下载按钮，避免逐行重复查库
     * @param file 已通过资源范围检查的文件
     * @return 是否具有相应下载功能权限
     */
    public boolean canDownloadAuthorizedFile(File file) {
        if (file.getBizType() == FileBizType.TEST_DATA) {
            return canAccessTestData();
        }
        return permissions.hasPermi("file:file:download") && (isPublic(file) || canAccessPrivateFiles());
    }

    /**
     * 通用删除仅处理没有业务归属的普通文件
     * @param file 文件元数据
     * @return 是否允许通用删除
     */
    public boolean canDelete(File file) {
        return file != null && file.getBizType() == FileBizType.GENERAL
                && (file.getBizId() == null || Long.valueOf(0L).equals(file.getBizId()))
                && permissions.hasPermi("file:file:remove")
                && (isPublic(file) || canAccessPrivateFiles());
    }

    /**
     * 校验普通上传功能及公开范围
     * @param publicAccess 是否公开
     */
    public void requireUpload(boolean publicAccess) {
        require(permissions.hasPermi("file:file:upload") && (publicAccess || canAccessPrivateFiles()));
    }

    /**
     * 校验直接下载入口
     * @param file 文件元数据
     */
    public void requireDownload(File file) {
        require(canDownload(file));
    }

    /**
     * 校验通用删除入口
     * @param file 文件元数据
     */
    public void requireDelete(File file) {
        require(canDelete(file));
    }

    /**
     * 判断文件是否为公开内容，测试数据始终私有
     * @param file 文件元数据
     * @return 是否公开
     */
    public boolean isPublic(File file) {
        return file.getBizType() != FileBizType.TEST_DATA
                && Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic());
    }

    /**
     * 业务专属文件按资源管理归属校验，不能用全局文件权限绕过
     * @param file 文件元数据
     * @return 是否可访问所属资源文件
     */
    private boolean canAccessResource(File file) {
        if (file.getBizType() == FileBizType.TEST_DATA || file.getBizType() == FileBizType.PROBLEM_ATTACHMENT) {
            Problem problem = problems.getById(file.getBizId());
            return problem != null && resourceAccess.canManageProblem(problem);
        }
        if (file.getBizType() == FileBizType.CONTEST_ATTACHMENT) {
            Contest contest = contests.getById(file.getBizId());
            return contest != null && resourceAccess.canManageContest(contest);
        }
        return true;
    }

    /**
     * 将功能或资源拒绝转换为统一鉴权异常
     * @param allowed 是否允许
     */
    private void require(boolean allowed) {
        if (!allowed) {
            throw new AccessDeniedException("访问权限不足");
        }
    }
}
