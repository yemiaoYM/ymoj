package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.mapper.ContestProblemMapper;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.vo.oj.ContestProblemTableVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ContestProblemEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  22:48
 * @Description: ContestProblemEntityServiceImpl
 */
@Service
public class ContestProblemEntityServiceImpl extends ServiceImpl<ContestProblemMapper, ContestProblem>
        implements ContestProblemEntityService {

    /**
     * 获取比赛题目列表
     * @param contestId 比赛id
     * @param userId 用户id
     * @return 比赛题目列表
     */
    @Override
    public List<ContestProblemTableVO> getContestProblemList(Long contestId, Long userId) {
        return getBaseMapper().getContestProblemList(contestId, userId);
    }
}
