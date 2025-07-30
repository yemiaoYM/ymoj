package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.TagEntityService;
import com.ymcloud.core.web.mapper.TagMapper;
import com.ymcloud.pojo.entity.Tag;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: TagEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  18:50
 * @Description: TagEntityServiceImpl
 */
@Service
public class TagEntityServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagEntityService {
}
