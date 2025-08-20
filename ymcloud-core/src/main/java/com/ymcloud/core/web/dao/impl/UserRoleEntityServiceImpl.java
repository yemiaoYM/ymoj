package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.UserRoleEntityService;
import com.ymcloud.core.web.mapper.UserRoleMapper;
import com.ymcloud.pojo.entity.UserRole;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: UserRoleEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  17:10
 * @Description: UserRoleEntityServiceImpl
 */
@Service
public class UserRoleEntityServiceImpl extends ServiceImpl<UserRoleMapper, UserRole>
        implements UserRoleEntityService {
}
