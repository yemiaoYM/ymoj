package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ProblemCaseEntityService;
import com.ymcloud.core.web.mapper.ProblemCaseMapper;
import com.ymcloud.pojo.entity.ProblemCase;
import org.springframework.stereotype.Service;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 题目当前测试清单实体访问实现
 */
@Service
public class ProblemCaseEntityServiceImpl extends ServiceImpl<ProblemCaseMapper, ProblemCase>
        implements ProblemCaseEntityService {}
