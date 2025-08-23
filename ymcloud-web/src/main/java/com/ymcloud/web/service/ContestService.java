package com.ymcloud.web.service;

import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.query.ContestPageQuery;
import com.ymcloud.pojo.query.ContestProblemSubmissionPageQuery;
import com.ymcloud.pojo.query.ContestRankPageQuery;
import com.ymcloud.pojo.query.ContestSubmissionPageQuery;
import com.ymcloud.pojo.vo.*;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: ContestService
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:47
 * @Description: ContestService
 */
public interface ContestService {

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

    /**
     * 查询比赛题目详情
     * @param contestId 比赛id
     * @param displayId 题目展示id
     * @return 比赛题目详情
     */
    ProblemVO getContestProblem(Long contestId, String displayId);

    /**
     * 查询比赛题目列表
     * @param contestId 比赛id
     * @return 比赛题目列表
     */
    List<ContestProblemTableVO> getContestProblemList(Long contestId);

    /**
     * 查询用户当前比赛题目提交列表
     * @param query 分页查询实体
     * @return 提交列表
     */
    PageVO<SubmissionTableVO> problemSubmissionList(ContestProblemSubmissionPageQuery query);

    /**
     * 获取比赛评测列表 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    PageVO<SubmissionTableVO> getSubmissionList(ContestSubmissionPageQuery query);

    /**
     * 获取比赛排名列表
     * @param query 查询请求题
     * @return 比赛排名列表
     */
    ContestRankTableVO getContestRankList(ContestRankPageQuery query);
}
