package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.core.web.mapper.TestDataVersionMapper;
import com.ymcloud.pojo.entity.TestDataVersion;
import org.springframework.stereotype.Service;

@Service
public class TestDataVersionEntityServiceImpl extends ServiceImpl<TestDataVersionMapper, TestDataVersion>
        implements TestDataVersionEntityService {
}
