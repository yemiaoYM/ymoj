package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.vo.oj.ContestRankVO;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: ContestEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-08-15  23:05
 * @Description: ContestEntityService
 */
public interface ContestEntityService extends IService<Contest> {

    /**
     * 查询指定比赛（ACM 模式）的排行榜数据
     *
     * @param contestId 比赛 ID
     * @return 该比赛的排行榜数据
     */
    Page<ContestRankVO> getAcmContestRank(IPage<ContestRankVO> page, Long contestId, String keyword);

    /**
     * 查询指定比赛（OI 模式）的排行榜数据
     *
     * @param contestId 比赛 ID
     * @return 该比赛的排行榜数据
     */
    Page<ContestRankVO> getOIContestRank(IPage<ContestRankVO> page, Long contestId, String keyword);

}
