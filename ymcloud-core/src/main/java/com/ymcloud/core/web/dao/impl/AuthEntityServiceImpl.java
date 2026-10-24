package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.AuthEntityService;
import com.ymcloud.core.web.mapper.AuthMapper;
import com.ymcloud.pojo.entity.Auth;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * 查询全部可展示菜单；调用权限由业务层检查。
     * @return 菜单列表，无匹配项时为空列表
     */
    @Override
    public List<Auth> selectMenuTreeAll() {
        return baseMapper.selectMenuTreeAll();
    }

    /**
     * 查询指定用户被授权的菜单。
     * @param userId 已认证用户编号
     * @return 菜单列表，无授权菜单时为空列表
     */
    @Override
    public List<Auth> selectMenuTreeByUserId(Long userId) {
        return baseMapper.selectMenuTreeByUserId(userId);
    }
}
