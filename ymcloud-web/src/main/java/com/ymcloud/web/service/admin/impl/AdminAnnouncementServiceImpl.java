package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.AnnouncementEntityService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.dto.admin.AnnouncementDTO;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminAnnouncementPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminAnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: AdminAnnouncementServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  17:06
 * @Description: 管理端公告服务实现类
 */
@Service
public class AdminAnnouncementServiceImpl implements AdminAnnouncementService {

    @Autowired
    private FileEntityService files;

    @Autowired
    private AnnouncementEntityService announcementEntityService;

    @Override
    public PageVO<Announcement> queryList(AdminAnnouncementPageQuery pageQuery) {
        //构建分页对象
        Page<Announcement> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        //构建查询条件
        Page<Announcement> resultPage = announcementEntityService.lambdaQuery()
                .like(StrUtil.isNotBlank(pageQuery.getTitle()), Announcement::getTitle, pageQuery.getTitle())
                .eq(pageQuery.getPinned() != null, Announcement::getPinned, pageQuery.getPinned())
                .eq(pageQuery.getStatus() != null, Announcement::getStatus, pageQuery.getStatus())
                .orderByDesc(Announcement::getPinned, Announcement::getPriority, Announcement::getUpdatedTime)
                .page(page);

        return PageVO.of(resultPage,Announcement.class);
    }

    @Override
    public Announcement getAnnouncementById(Long id) {
        if (id == null || id <= 0) {
            throw new ServiceException("公告ID不能为空或无效");
        }
        Announcement announcement = announcementEntityService.getById(id);
        if (announcement == null) {
            throw new ServiceException("此公告不存在");
        }
        return announcement;
    }

    /**
     * 保存公告并绑定本次提交的正文图片
     * @param announcementDTO 公告内容与预上传图片主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAnnouncement(AnnouncementDTO announcementDTO) {
        //获取当前登录用户信息
        User currentUser = SecurityUtils.getLoginUser().getUser();
        //构建公告实体
        Announcement announcement = BeanUtil.copyProperties(announcementDTO, Announcement.class);
        announcement.setAuthorId(currentUser.getId());
        announcement.setAuthorUsername(currentUser.getUsername());
        //保存公告
        boolean success = announcementEntityService.save(announcement);
        if (!success) {
            throw new ServiceException("新增公告失败，请稍后再试");
        }
        if (CollUtil.isNotEmpty(announcementDTO.getFileIds())) {
            files.bindImages(announcementDTO.getFileIds(), FileBizType.ANNOUNCEMENT, announcement.getId(), currentUser.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(Long id, AnnouncementDTO announcementDTO) {
        if (id == null || id <= 0) {
            throw new ServiceException("公告ID不能为空或无效");
        }
        //检查公告是否存在
        Announcement existingAnnouncement = announcementEntityService.getById(id);
        if (existingAnnouncement == null) {
            throw new ServiceException("公告不存在");
        }
        //更新公告信息
        Announcement announcement = BeanUtil.copyProperties(announcementDTO, Announcement.class);
        announcement.setId(id);
        //保存更新
        boolean success = announcementEntityService.updateById(announcement);
        if (!success) {
            throw new ServiceException("修改公告失败,请稍后再试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnouncementById(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw new ServiceException("删除的公告ID列表不能为空");
        }
        //检查公告是否存在
        List<Announcement> existingAnnouncements = announcementEntityService.listByIds(ids);
        if (existingAnnouncements.size() != ids.size()) {
            throw new ServiceException("部分公告不存在，无法删除");
        }
        //删除公告
        boolean success = announcementEntityService.removeByIds(ids);
        if (!success) {
            throw new ServiceException("删除公告失败,请稍后再试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncementStatus(Long id, Boolean status) {
        if (id == null || id <= 0) {
            throw new ServiceException("公告ID不能为空或无效");
        }
        if (status == null) {
            throw new ServiceException("公告状态不能为空");
        }
        //检查公告是否存在
        Announcement existingAnnouncement = announcementEntityService.getById(id);
        if (existingAnnouncement == null) {
            throw new ServiceException("公告不存在");
        }
        //更新状态
        boolean success = announcementEntityService.lambdaUpdate()
                .eq(Announcement::getId, id)
                .set(Announcement::getStatus, status)
                .update();
        if (!success) {
            throw new ServiceException("修改公告状态失败,请稍后再试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncementPinned(Long id, Boolean pinned) {
        if (id == null || id <= 0) {
            throw new ServiceException("公告ID不能为空或无效");
        }
        if (pinned == null) {
            throw new ServiceException("置顶状态不能为空");
        }
        //检查公告是否存在
        Announcement existingAnnouncement = announcementEntityService.getById(id);
        if (existingAnnouncement == null) {
            throw new ServiceException("公告不存在");
        }
        //更新置顶状态
        boolean success = announcementEntityService.lambdaUpdate()
                .eq(Announcement::getId, id)
                .set(Announcement::getPinned, pinned)
                .update();
        if (!success) {
            throw new ServiceException("修改公告置顶状态失败,请稍后再试");
        }
    }
}
