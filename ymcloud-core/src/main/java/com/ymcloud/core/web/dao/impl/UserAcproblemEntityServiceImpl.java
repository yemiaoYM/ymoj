package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.core.web.mapper.UserAcproblemMapper;
import com.ymcloud.pojo.entity.UserAcproblem;
import com.ymcloud.pojo.vo.oj.UserAcRankVO;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * 按去重后的 AC 题目数降序查询前十名用户，同分按用户编号排序。
     * @return 用户 AC 排名，无 AC 记录时为空列表
     */
    @Override
    public List<UserAcRankVO> listUserAcRank() {
        return baseMapper.selectUserAcRank();
    }
}
