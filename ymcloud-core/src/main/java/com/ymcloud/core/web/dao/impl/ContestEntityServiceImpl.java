package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.mapper.ContestMapper;
import com.ymcloud.pojo.entity.Contest;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ContestEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-15  23:05
 * @Description: ContestEntityServiceImpl
 */
@Service
public class ContestEntityServiceImpl extends ServiceImpl<ContestMapper, Contest>
        implements ContestEntityService {

}
