package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.vo.oj.ContestRankVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: ContestMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:43
 * @Description: ContestMapper
 */
@Mapper
public interface ContestMapper extends BaseMapper<Contest> {

    /**
     * 查询指定比赛（ACM 模式）的排行榜数据
     *
     * @param contestId 比赛 ID
     * @return 该比赛的排行榜数据
     */
    Page<ContestRankVO> getAcmContestRank(IPage<ContestRankVO> page,
                                          @Param("contestId") Long contestId,
                                          @Param("keyword") String keyword);

    /**
     * 查询指定比赛（OI 模式）的排行榜数据
     *
     * @param contestId 比赛 ID
     * @return 该比赛的排行榜数据
     */
    Page<ContestRankVO> getOIContestRank(IPage<ContestRankVO> page,
                                         @Param("contestId") Long contestId,
                                         @Param("keyword") String keyword);
}
