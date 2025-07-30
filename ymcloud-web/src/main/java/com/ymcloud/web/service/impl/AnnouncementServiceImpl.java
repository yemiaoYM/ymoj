package com.ymcloud.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.mapper.AnnouncementMapper;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.web.service.AnnouncementService;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: AnnouncementServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  15:34
 * @Description: AnnouncementServiceImpl
 */
@Service
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement>
        implements AnnouncementService {

}
