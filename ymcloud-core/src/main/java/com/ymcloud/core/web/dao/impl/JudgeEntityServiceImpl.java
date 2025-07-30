package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.pojo.entity.Judge;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: JudgeEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  00:52
 * @Description: JudgeEntityServiceImpl
 */
@Service
public class JudgeEntityServiceImpl extends ServiceImpl<JudgeMapper, Judge>
        implements JudgeEntityService {
}
