package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.bo.ContestSubmissionRecord;
import com.ymcloud.pojo.bo.ProblemAccessScope;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.query.oj.SubmissionPageQuery;
import com.ymcloud.pojo.vo.oj.ProblemCountVO;

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

    /**
     * 在分页前过滤无原题访问权的普通提交
     * @param page 分页参数
     * @param query 提交筛选条件
     * @param scope 服务端生成的题目访问范围
     * @return 授权的提交分页
     */
    Page<Judge> pagePublicSubmissions(Page<Judge> page, SubmissionPageQuery query, ProblemAccessScope scope);

    /**
     * 仅查询管理范围内的提交时间与状态供后台统计
     * @param since 统计开始时间
     * @param scope 当前用户管理范围
     * @return 提交统计基础数据
     */
    List<Judge> listDashboardSubmissions(LocalDateTime since, ProblemAccessScope scope);
}
