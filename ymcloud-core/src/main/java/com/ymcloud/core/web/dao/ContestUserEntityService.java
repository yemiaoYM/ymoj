package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.vo.oj.ContestCountVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: ContestUserEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-08-13  01:45
 * @Description: ContestUserEntityService
 */
public interface ContestUserEntityService extends IService<ContestUser> {

    /**
     * 根据比赛id列表查询每个比赛的参与人数
     * @param contestIds 比赛id列表
     * @return 每个比赛的参与人数
     */
    List<ContestCountVO> getCountByContestIds(List<Long> contestIds);
}
