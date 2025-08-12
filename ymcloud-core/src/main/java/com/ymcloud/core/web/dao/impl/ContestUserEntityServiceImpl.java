package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.mapper.ContestUserMapper;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.vo.ContestCountVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ContestUserEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-13  01:46
 * @Description: ContestUserEntityServiceImpl
 */
@Service
public class ContestUserEntityServiceImpl extends ServiceImpl<ContestUserMapper, ContestUser>
        implements ContestUserEntityService {

    /**
     * 根据比赛id列表查询每个比赛的参与人数
     * @param contestIds 比赛id列表
     * @return 每个比赛的参与人数
     */
    @Override
    public List<ContestCountVO> getCountByContestIds(List<Long> contestIds){
        return baseMapper.getCountByContestIds(contestIds);
    }

}
