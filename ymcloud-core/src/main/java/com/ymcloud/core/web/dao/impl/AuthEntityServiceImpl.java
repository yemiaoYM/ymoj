package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.AuthEntityService;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.pojo.entity.Auth;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: AuthEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  17:08
 * @Description: AuthEntityServiceImpl
 */
@Service
public class AuthEntityServiceImpl extends ServiceImpl<AuthMapper, Auth>
        implements AuthEntityService {
}
