package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.bo.ContestSubmissionRecord;
import com.ymcloud.pojo.vo.oj.ProblemCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: JudgeMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  00:33
 * @Description: JudgeMapper
 */
@Mapper
public interface JudgeMapper extends BaseMapper<Judge> {

    /**
     * 批量查询题目的提交情况
     * @param problemIds 题目唯一表示列表
     * @return 题目提交情况列表
     */
    List<ProblemCountVO> getProblemListCount(@Param("problemIds") List<String> problemIds);

    /**
     * 查询比赛提交记录列表
     * @param contestId 比赛id
     * @param endTime 比赛结束时间
     * @param keyword 用户名称或者学校名称
     * @return 赛提交记录列表
     */
    List<ContestSubmissionRecord> getContestSubmissionRecordList(@Param("contestId") Long contestId,
                                                                 @Param("endTime") LocalDateTime endTime,
                                                                 @Param("keyword") String keyword);

}
