package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.AnnouncementEntityService;
import com.ymcloud.core.web.mapper.AnnouncementMapper;
import com.ymcloud.pojo.entity.Announcement;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: AnnouncementEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:50
 * @Description: AnnouncementEntityServiceImpl
 */
@Service
public class AnnouncementEntityServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement>
        implements AnnouncementEntityService {
}
