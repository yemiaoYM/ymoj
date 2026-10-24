package com.ymcloud.web.service.oj.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.AnnouncementEntityService;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.AnnouncementVO;
import com.ymcloud.web.service.oj.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 公开公告分页查询
 */
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final int PAGE_SIZE = 5;

    @Autowired
    private AnnouncementEntityService announcementEntityService;

    /**
     * 查询可见公告，按置顶、权重、更新时间、ID降序稳定分页。
     * @param pageNo 页码至少1；每页固定5条
     * @return 分页公告；无数据时records为空列表
     */
    @Override
    public PageVO<AnnouncementVO> pageQuery(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            throw new ServiceException("公告页码不能小于1", HttpStatus.BAD_REQUEST);
        }
        Page<Announcement> page = announcementEntityService.lambdaQuery()
                .select(Announcement::getId, Announcement::getTitle, Announcement::getContent,
                        Announcement::getPinned, Announcement::getAuthorUsername, Announcement::getUpdatedTime)
                .eq(Announcement::getStatus, true)
                .orderByDesc(Announcement::getPinned, Announcement::getPriority,
                        Announcement::getUpdatedTime, Announcement::getId)
                .page(Page.of(pageNo, PAGE_SIZE));
        return PageVO.of(page, AnnouncementVO.class);
    }
}
