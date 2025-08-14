package com.ymcloud.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.query.ContestPageQuery;
import com.ymcloud.pojo.vo.ContestTableVO;
import com.ymcloud.pojo.vo.ContestVO;
import com.ymcloud.pojo.vo.PageVO;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: ContestService
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:47
 * @Description: ContestService
 */
public interface ContestService extends IService<Contest> {

    /**
     * 获取比赛列表表单 分页查询
     * @param query 查询请求体
     * @return 比赛列表
     */
    PageVO<ContestTableVO> pageQuery(ContestPageQuery query);

    /**
     * 获取比赛详情
     * @param contestId 比赛id
     * @return 比赛详情
     */
    ContestVO getContestInfo(Long contestId);

    /**
     * 注册进入比赛
     * @param registerContestDTO 请求体
     */
    void registerContest(RegisterContestDTO registerContestDTO);
}
