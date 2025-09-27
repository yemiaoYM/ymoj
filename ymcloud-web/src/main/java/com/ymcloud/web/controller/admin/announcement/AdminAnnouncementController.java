package com.ymcloud.web.controller.admin.announcement;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.AnnouncementDTO;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.pojo.query.admin.AdminAnnouncementPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminAnnouncementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.admin.announcement
 * @ClassName: AdminAnnouncementController
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  17:05
 * @Description: 公告管理
 */
@Slf4j
@RestController
@RequestMapping("/admin/announcement")
public class AdminAnnouncementController {

    @Autowired
    private AdminAnnouncementService adminAnnouncementService;

    /**
     * 查询公告列表
     * @param pageQuery 查询条件
     * @return 公告列表
     */
    @PreAuthorize("hasAuthority('announcement:announcement:list')")
    @GetMapping("/list")
    public Result<PageVO<Announcement>> queryList(@Validated AdminAnnouncementPageQuery pageQuery) {
        log.info("查询公告列表:{}", pageQuery);
        PageVO<Announcement> page = adminAnnouncementService.queryList(pageQuery);
        return Result.ok(page);
    }

    /**
     * 根据ID查询公告
     * @param id 公告ID
     * @return 公告信息
     */
    @PreAuthorize("hasAuthority('announcement:announcement:query')")
    @GetMapping("/{id}")
    public Result<Announcement> getAnnouncementById(@PathVariable("id") Long id) {
        log.info("根据ID查询公告:{}", id);
        Announcement announcement = adminAnnouncementService.getAnnouncementById(id);
        return Result.ok(announcement);
    }

    /**
     * 新增公告
     * @param announcementDTO 公告信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('announcement:announcement:add')")
    @PostMapping
    public Result<Void> addAnnouncement(@Validated @RequestBody AnnouncementDTO announcementDTO) {
        log.info("新增公告:{}", announcementDTO);
        adminAnnouncementService.addAnnouncement(announcementDTO);
        return Result.ok();
    }

    /**
     * 修改公告
     * @param id 公告ID
     * @param announcementDTO 公告信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('announcement:announcement:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateAnnouncement(@PathVariable("id") Long id, @Validated @RequestBody AnnouncementDTO announcementDTO) {
        log.info("修改公告:{}, 数据:{}", id, announcementDTO);
        adminAnnouncementService.updateAnnouncement(id, announcementDTO);
        return Result.ok();
    }

    /**
     * 根据ID删除公告
     * @param ids 公告ID列表
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('announcement:announcement:remove')")
    @DeleteMapping("/{ids}")
    public Result<Void> deleteAnnouncementById(@PathVariable("ids") List<Long> ids) {
        log.info("根据ID删除公告:{}", ids);
        adminAnnouncementService.deleteAnnouncementById(ids);
        return Result.ok();
    }

    /**
     * 修改公告状态
     * @param id 公告ID
     * @param status 状态：true=可见，false=不可见
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('announcement:announcement:edit')")
    @PutMapping("/{id}/status")
    public Result<Void> updateAnnouncementStatus(@PathVariable("id") Long id, @RequestParam("status") Boolean status) {
        log.info("修改公告状态: id={}, status={}", id, status);
        adminAnnouncementService.updateAnnouncementStatus(id, status);
        return Result.ok();
    }

    /**
     * 修改公告置顶状态
     * @param id 公告ID
     * @param pinned 置顶状态：true=置顶，false=不置顶
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('announcement:announcement:edit')")
    @PutMapping("/{id}/pinned")
    public Result<Void> updateAnnouncementPinned(@PathVariable("id") Long id, @RequestParam("pinned") Boolean pinned) {
        log.info("修改公告置顶状态: id={}, pinned={}", id, pinned);
        adminAnnouncementService.updateAnnouncementPinned(id, pinned);
        return Result.ok();
    }
}
