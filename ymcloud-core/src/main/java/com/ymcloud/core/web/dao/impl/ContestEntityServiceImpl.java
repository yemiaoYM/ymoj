package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.mapper.ContestMapper;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.vo.oj.ContestRankVO;
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

    /**
     * 查询指定比赛（ACM 模式）的排行榜数据
     *
     * @param contestId 比赛 ID
     * @return 该比赛的排行榜数据
     */
    @Override
    public Page<ContestRankVO> getAcmContestRank(IPage<ContestRankVO> page, Long contestId, String keyword) {
        return getBaseMapper().getAcmContestRank(page, contestId, keyword);
    }

    /**
     * 查询指定比赛（OI 模式）的排行榜数据
     *
     * @param contestId 比赛 ID
     * @return 该比赛的排行榜数据
     */
    @Override
    public Page<ContestRankVO> getOIContestRank(IPage<ContestRankVO> page, Long contestId, String keyword) {
        return getBaseMapper().getOIContestRank(page, contestId, keyword);
    }
}
