package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.mapper.ContestProblemMapper;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.vo.oj.ContestProblemTableVO;
import org.springframework.stereotype.Service;

import java.util.List;


/**
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

    /**
     * 通过单条SQL更新比赛题目展示编号，不修改标题、颜色及原题关联
     * @param contestId 已锁定的比赛ID
     * @param problems 已校验的非空题目关联ID及目标展示编号列表
     */
    @Override
    public void updateDisplayIds(Long contestId, List<ContestProblem> problems) {
        baseMapper.updateDisplayIds(contestId, problems);
    }
}
