package com.ymcloud.web.service.admin;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 文件管理权限边界：测试数据始终使用题目权限，普通私有文件使用独立私有权限。
 */
@Component
public class FileAccessPolicy {

    private final PermissionService permissions;

    public FileAccessPolicy(PermissionService permissions) {
        this.permissions = permissions;
    }

    public boolean canListGeneralFiles() {
        return permissions.hasPermi("file:file:list");
    }

    public boolean canAccessPrivateFiles() {
        return permissions.hasPermi("file:file:private");
    }

    public boolean canAccessTestData() {
        return permissions.hasPermi("problem:testcase:query");
    }

    public boolean canList(File file) {
        if (file == null) {
            return false;
        }
        if (file.getBizType() == FileBizType.TEST_DATA) {
            return canAccessTestData();
        }
        return canListGeneralFiles() && (isPublic(file) || canAccessPrivateFiles());
    }

    public boolean canDownload(File file) {
        if (file == null) {
            return false;
        }
        if (file.getBizType() == FileBizType.TEST_DATA) {
            return canAccessTestData();
        }
        return permissions.hasPermi("file:file:download") && (isPublic(file) || canAccessPrivateFiles());
    }

    public boolean canDelete(File file) {
        return file != null && file.getBizType() == FileBizType.GENERAL
                && (file.getBizId() == null || Long.valueOf(0L).equals(file.getBizId()))
                && permissions.hasPermi("file:file:remove")
                && (isPublic(file) || canAccessPrivateFiles());
    }

    public void requireUpload(boolean publicAccess) {
        require(permissions.hasPermi("file:file:upload") && (publicAccess || canAccessPrivateFiles()));
    }

    public void requireDownload(File file) {
        require(canDownload(file));
    }

    public void requireDelete(File file) {
        require(canDelete(file));
    }

    public boolean isPublic(File file) {
        return file.getBizType() != FileBizType.TEST_DATA
                && Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic());
    }

    private void require(boolean allowed) {
        if (!allowed) {
            throw new AccessDeniedException("访问权限不足");
        }
    }
}
