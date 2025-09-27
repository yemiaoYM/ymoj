package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.AnnouncementDTO;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.pojo.query.admin.AdminAnnouncementPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: AdminAnnouncementService
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  17:06
 * @Description: 管理端公告服务接口
 */
public interface AdminAnnouncementService {

    /**
     * 分页查询公告列表
     * @param pageQuery 查询条件
     * @return 公告列表
     */
    PageVO<Announcement> queryList(AdminAnnouncementPageQuery pageQuery);

    /**
     * 根据ID查询公告详情
     * @param id 公告ID
     * @return 公告信息
     */
    Announcement getAnnouncementById(Long id);

    /**
     * 新增公告
     * @param announcementDTO 公告信息
     */
    void addAnnouncement(AnnouncementDTO announcementDTO);

    /**
     * 修改公告
     * @param id 公告ID
     * @param announcementDTO 公告信息
     */
    void updateAnnouncement(Long id, AnnouncementDTO announcementDTO);

    /**
     * 根据ID删除公告
     * @param ids 公告ID列表
     */
    void deleteAnnouncementById(List<Long> ids);

    /**
     * 修改公告状态
     * @param id 公告ID
     * @param status 状态：true=可见，false=不可见
     */
    void updateAnnouncementStatus(Long id, Boolean status);

    /**
     * 修改公告置顶状态
     * @param id 公告ID
     * @param pinned 置顶状态：true=置顶，false=不置顶
     */
    void updateAnnouncementPinned(Long id, Boolean pinned);
}
