package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.core.web.mapper.TestDataVersionMapper;
import com.ymcloud.pojo.entity.TestDataVersion;
import org.springframework.stereotype.Service;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 不可变测试数据版本实体访问实现
 */
@Service
public class TestDataVersionEntityServiceImpl extends ServiceImpl<TestDataVersionMapper, TestDataVersion>
        implements TestDataVersionEntityService {}
