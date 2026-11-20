package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.ProblemCase;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 题目当前测试清单持久映射
 */
@Mapper
public interface ProblemCaseMapper extends BaseMapper<ProblemCase> {}
