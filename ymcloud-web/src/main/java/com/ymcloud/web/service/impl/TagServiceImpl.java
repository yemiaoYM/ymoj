package com.ymcloud.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.mapper.TagMapper;
import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.web.service.TagService;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: TagServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  18:46
 * @Description: TagServiceImpl
 */
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {
}
