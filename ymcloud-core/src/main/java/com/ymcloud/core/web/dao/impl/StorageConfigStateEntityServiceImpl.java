package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.StorageConfigStateEntityService;
import com.ymcloud.core.web.mapper.StorageConfigStateMapper;
import com.ymcloud.pojo.entity.StorageConfigState;
import org.springframework.stereotype.Service;

@Service
public class StorageConfigStateEntityServiceImpl
        extends ServiceImpl<StorageConfigStateMapper, StorageConfigState>
        implements StorageConfigStateEntityService {
}
