package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.mapper.JudgeCaseMapper;
import com.ymcloud.pojo.entity.JudgeCase;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: JudgeCaseEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-03  20:48
 * @Description: JudgeCaseEntityServiceImpl
 */
@Service
public class JudgeCaseEntityServiceImpl extends ServiceImpl<JudgeCaseMapper, JudgeCase>
        implements JudgeCaseEntityService {
}
