package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.mapper.ContestProblemMapper;
import com.ymcloud.pojo.entity.ContestProblem;
import org.springframework.stereotype.Service;


/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ContestProblemEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  22:48
 * @Description: ContestProblemEntityServiceImpl
 */
@Service
public class ContestProblemEntityServiceImpl extends ServiceImpl<ContestProblemMapper, ContestProblem>
        implements ContestProblemEntityService {
}
