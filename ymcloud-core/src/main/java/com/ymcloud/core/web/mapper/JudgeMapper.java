package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.pojo.bo.ContestSubmissionRecord;
import com.ymcloud.pojo.bo.ProblemAccessScope;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.query.oj.SubmissionPageQuery;
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

    /**
     * 在分页前过滤无原题访问权的普通提交
     * @param page 分页参数
     * @param query 提交筛选条件
     * @param scope 服务端生成的题目访问范围
     * @return 授权的提交分页
     */
    Page<Judge> pagePublicSubmissions(@Param("page") Page<Judge> page,
                                     @Param("query") SubmissionPageQuery query,
                                     @Param("scope") ProblemAccessScope scope);

    /**
     * 仅查询管理范围内的提交时间与状态供后台统计
     * @param since 统计开始时间
     * @param scope 当前用户管理范围
     * @return 提交统计基础数据
     */
    List<Judge> listDashboardSubmissions(@Param("since") LocalDateTime since, @Param("scope") ProblemAccessScope scope);
}
