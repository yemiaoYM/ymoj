package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.core.web.mapper.UserMapper;
import com.ymcloud.pojo.entity.User;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: UserEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  20:28
 * @Description: UserEntityServiceImpl
 */
@Service
public class UserEntityServiceImpl extends ServiceImpl<UserMapper, User> implements UserEntityService {
}
