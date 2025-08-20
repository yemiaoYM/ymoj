package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.RoleEntityService;
import com.ymcloud.core.web.mapper.RoleMapper;
import com.ymcloud.pojo.entity.Role;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: RoleEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  17:05
 * @Description: RoleEntityServiceImpl
 */
@Service
public class RoleEntityServiceImpl extends ServiceImpl<RoleMapper, Role>
        implements RoleEntityService {

}
