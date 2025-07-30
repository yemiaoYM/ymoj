package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ProblemCaseEntityService;
import com.ymcloud.core.web.mapper.ProblemCaseMapper;
import com.ymcloud.pojo.entity.ProblemCase;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ProblemCaseEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  14:42
 * @Description: ProblemCaseEntityServiceImpl
 */
@Service
public class ProblemCaseEntityServiceImpl extends ServiceImpl<ProblemCaseMapper, ProblemCase>
        implements ProblemCaseEntityService {
}
