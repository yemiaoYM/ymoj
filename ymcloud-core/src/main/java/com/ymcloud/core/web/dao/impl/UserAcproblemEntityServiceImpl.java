package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.core.web.mapper.UserAcproblemMapper;
import com.ymcloud.pojo.entity.UserAcproblem;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: UserAcproblemEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  15:25
 * @Description: UserAcproblemEntityServiceImpl
 */
@Service
public class UserAcproblemEntityServiceImpl extends ServiceImpl<UserAcproblemMapper, UserAcproblem>
        implements UserAcproblemEntityService {
}
