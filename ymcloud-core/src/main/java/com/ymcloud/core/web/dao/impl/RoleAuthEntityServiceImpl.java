package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.RoleAuthEntityService;
import com.ymcloud.core.web.mapper.RoleAuthMapper;
import com.ymcloud.pojo.entity.RoleAuth;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: RoleAuthEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  17:12
 * @Description: RoleAuthEntityServiceImpl
 */
@Service
public class RoleAuthEntityServiceImpl extends ServiceImpl<RoleAuthMapper, RoleAuth>
        implements RoleAuthEntityService {
}
