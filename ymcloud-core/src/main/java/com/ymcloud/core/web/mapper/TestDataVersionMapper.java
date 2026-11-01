package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.TestDataVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 不可变测试数据版本持久映射
 */
@Mapper
public interface TestDataVersionMapper extends BaseMapper<TestDataVersion> {}
