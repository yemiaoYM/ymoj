package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.bo.ContestSubmissionRecord;
import com.ymcloud.pojo.vo.ProblemCountVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: JudgeEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  00:52
 * @Description: JudgeEntityService
 */
public interface JudgeEntityService extends IService<Judge> {

    /**
     * 批量查询题目的提交情况
     * @param problemIds 题目唯一表示列表
     * @return 题目提交情况列表
     */
    List<ProblemCountVO> getProblemListCount(List<String> problemIds);

    /**
     * 查询比赛提交记录列表
     * @param contestId 比赛id
     * @param endTime 比赛结束时间
     * @param keyword 用户名称或者学校名称
     * @return 赛提交记录列表
     */
    List<ContestSubmissionRecord> getContestSubmissionRecordList(Long contestId, LocalDateTime endTime,String keyword);
}
