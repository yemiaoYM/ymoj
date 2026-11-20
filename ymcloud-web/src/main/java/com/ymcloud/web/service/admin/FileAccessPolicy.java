package com.ymcloud.web.service.admin;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.AnnouncementEntityService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.FileInfo;
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

    @Autowired
    private AnnouncementEntityService announcements;

    @Autowired
    private ContestProblemEntityService contestProblems;

    /**
     * 按正文所属资源检查展示权限，未绑定的草稿只允许创建人预览
     * @param file 登记的正文文件
     * @param contestId 当前比赛上下文，仅用于验证题目在该场比赛中可读
     */
    public void requireRead(FileInfo file, Long contestId) {
        if (file.getBizType() == FileBizType.GENERAL) {
            return;
        }
        if (file.getBizType() == FileBizType.TEST_DATA) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        Long id = file.getBizId();
        if (id != null && file.getBizType() == FileBizType.ANNOUNCEMENT) {
            Announcement announcement = announcements.getById(id);
            if (announcement != null && Boolean.TRUE.equals(announcement.getStatus())) {
                return;
            }
        }
        var auth = SecurityUtils.getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof LoginUser)) {
            throw new FileException("请登录后访问文件", HttpStatus.UNAUTHORIZED);
        }
        if (id == null) {
            require(SecurityUtils.getUserId().equals(file.getCreatedBy()));
        } else if (file.getBizType() == FileBizType.PROBLEM) {
            Problem problem = problems.getById(id);
            require(problem != null);
            if (contestId == null) {
                resourceAccess.requireReadProblem(problem);
            } else {
                Contest contest = contests.getById(contestId);
                require(contest != null && contestProblems.lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getProblemId, id)
                        .exists());
                resourceAccess.requireContestContent(contest);
            }
        } else if (file.getBizType() == FileBizType.CONTEST) {
            Contest contest = contests.getById(id);
            require(contest != null);
            resourceAccess.requireContestAccess(contest);
        } else {
            require(file.getBizType() == FileBizType.ANNOUNCEMENT && announcements.getById(id) != null
                    && permissions.hasPermi("announcement:announcement:query"));
        }
    }

    /**
     * 校验正文图片的新增或编辑权限，编辑已有资源还需检查归属
     * @param type 由上传端点确定的正文业务类别
     * @param id 已有业务记录主键，新建草稿时为空
     */
    public void requireImageUpload(FileBizType type, Long id) {
        String action = id == null ? "add" : "edit";
        switch (type) {
            case PROBLEM -> {
                require(permissions.hasPermi("problem:problem:" + action));
                resourceAccess.requireProblemManager();
                if (id != null) {
                    Problem problem = problems.getById(id);
                    require(problem != null && resourceAccess.canManageProblem(problem));
                }
            }
            case CONTEST -> {
                require(permissions.hasPermi("contest:contest:" + action));
                resourceAccess.requireContestManager();
                if (id != null) {
                    Contest contest = contests.getById(id);
                    require(contest != null && resourceAccess.canManageContest(contest));
                }
            }
            case ANNOUNCEMENT -> require(permissions.hasPermi("announcement:announcement:" + action)
                    && (id == null || announcements.getById(id) != null));
            default -> throw new FileException("不支持的正文业务类型", HttpStatus.BAD_REQUEST);
        }
    }

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
    public boolean canList(FileInfo file) {
        if (file == null) {
            return false;
        }
        if (file.getBizType() == FileBizType.TEST_DATA) {
            return canAccessTestData() && canAccessResource(file);
        }
        return canListGeneralFiles() && (file.getBizType() == FileBizType.GENERAL || canAccessPrivateFiles()) && canAccessResource(file);
    }

    /**
     * 检查下载功能及所属资源
     * @param file 文件元数据
     * @return 是否允许下载
     */
    public boolean canDownload(FileInfo file) {
        return file != null && canDownloadAuthorizedFile(file) && canAccessResource(file);
    }

    /**
     * 为已经通过 SQL 资源过滤的列表计算下载按钮，避免逐行重复查库
     * @param file 已通过资源范围检查的文件
     * @return 是否具有相应下载功能权限
     */
    public boolean canDownloadAuthorizedFile(FileInfo file) {
        if (file.getBizType() == FileBizType.TEST_DATA) {
            return canAccessTestData();
        }
        return permissions.hasPermi("file:file:download") && (file.getBizType() == FileBizType.GENERAL || canAccessPrivateFiles());
    }

    /**
     * 通用删除仅处理没有业务归属的普通文件
     * @param file 文件元数据
     * @return 是否允许通用删除
     */
    public boolean canDelete(FileInfo file) {
        return file != null && file.getBizType() == FileBizType.GENERAL
                && (file.getBizId() == null || Long.valueOf(0L).equals(file.getBizId()))
                && permissions.hasPermi("file:file:remove");
    }

    /**
     * 校验普通上传功能及公开范围
     */
    public void requireUpload() {
        require(permissions.hasPermi("file:file:upload"));
    }

    /**
     * 校验直接下载入口
     * @param file 文件元数据
     */
    public void requireDownload(FileInfo file) {
        require(canDownload(file));
    }

    /**
     * 校验通用删除入口
     * @param file 文件元数据
     */
    public void requireDelete(FileInfo file) {
        require(canDelete(file));
    }

    /**
     * 业务专属文件按资源管理归属校验，不能用全局文件权限绕过
     * @param file 文件元数据
     * @return 是否可访问所属资源文件
     */
    private boolean canAccessResource(FileInfo file) {
        if (file.getBizType() == FileBizType.TEST_DATA || file.getBizType() == FileBizType.PROBLEM) {
            Problem problem = problems.getById(file.getBizId());
            return problem != null && resourceAccess.canManageProblem(problem);
        }
        if (file.getBizType() == FileBizType.CONTEST) {
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
