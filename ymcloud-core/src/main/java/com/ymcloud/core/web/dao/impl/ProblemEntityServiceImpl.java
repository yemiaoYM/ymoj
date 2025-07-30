package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.mapper.ProblemMapper;
import com.ymcloud.pojo.entity.Problem;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ProblemEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  20:23
 * @Description: ProblemEntityServiceImpl
 */
@Service
public class ProblemEntityServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemEntityService {
}
