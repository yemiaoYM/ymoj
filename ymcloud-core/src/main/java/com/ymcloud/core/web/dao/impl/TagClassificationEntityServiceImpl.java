package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.TagClassificationEntityService;
import com.ymcloud.core.web.mapper.TagClassificationMapper;
import com.ymcloud.pojo.entity.TagClassification;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: TagClassificationEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  16:37
 * @Description: TagClassificationEntityServiceImpl
 */
@Service
public class TagClassificationEntityServiceImpl extends ServiceImpl<TagClassificationMapper, TagClassification>
        implements TagClassificationEntityService {
}
