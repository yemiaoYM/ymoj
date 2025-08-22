package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.core.web.mapper.ProblemTagMapper;
import com.ymcloud.pojo.entity.ProblemTag;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ProblemTagEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-22  20:47
 * @Description: ProblemTagEntityServiceImpl
 */
@Service
public class ProblemTagEntityServiceImpl extends ServiceImpl<ProblemTagMapper, ProblemTag>
        implements ProblemTagEntityService {
}
