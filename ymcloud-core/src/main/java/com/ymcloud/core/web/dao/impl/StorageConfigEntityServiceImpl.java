package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.StorageConfigEntityService;
import com.ymcloud.core.web.mapper.StorageConfigMapper;
import com.ymcloud.pojo.entity.StorageConfig;
import org.springframework.stereotype.Service;

@Service
public class StorageConfigEntityServiceImpl extends ServiceImpl<StorageConfigMapper, StorageConfig>
        implements StorageConfigEntityService {
}
