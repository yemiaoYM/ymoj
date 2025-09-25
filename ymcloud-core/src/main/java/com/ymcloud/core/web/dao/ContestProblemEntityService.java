package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.vo.oj.ContestProblemTableVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: ContestProblemEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  22:47
 * @Description: ContestProblemEntityService
 */
public interface ContestProblemEntityService extends IService<ContestProblem> {

    /**
     * 获取比赛题目列表
     * @param contestId 比赛id
     * @param userId 用户id
     * @param endTime 比赛结束时间
     * @return 比赛题目列表
     */
    List<ContestProblemTableVO> getContestProblemList(Long contestId, Long userId, LocalDateTime endTime);

}
