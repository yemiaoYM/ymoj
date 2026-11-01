package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.vo.oj.ContestProblemTableVO;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  22:47
 * @Description: ContestProblemEntityService
 */
public interface ContestProblemEntityService extends IService<ContestProblem> {

    /**
     * 获取比赛题目列表
     * @param contestId 比赛id
     * @param userId 用户id
     * @return 比赛题目列表
     */
    List<ContestProblemTableVO> getContestProblemList(Long contestId, Long userId);

    /**
     * 一次更新指定比赛的题目展示编号，调用方已锁定比赛并校验完整集合
     * @param contestId 比赛ID
     * @param problems 非空的题目关联ID及目标展示编号列表
     */
    void updateDisplayIds(Long contestId, List<ContestProblem> problems);

    /**
     * 获取比赛当前最大展示编号，用于新增题目编号
     * @param contestId 比赛 ID
     * @return 最后一个题目关联
     * 空比赛为 null
     */
    ContestProblem getLastDisplayProblem(Long contestId);
}
