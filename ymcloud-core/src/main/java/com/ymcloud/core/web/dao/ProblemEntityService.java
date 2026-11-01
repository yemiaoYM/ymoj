package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.bo.ProblemAccessScope;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  20:23
 * @Description: 题目实体访问与分页查询契约
 */
public interface ProblemEntityService extends IService<Problem> {
    /**
     * 题单列表分页查询
     * @param page   分页
     * @param keyword  题目/题号 支持模糊查询
     * @param difficulty 难度
     * @param tagIds 必须同时包含的标签 ID，空集合表示不筛选
     * @param scope 服务端生成的访问范围
     * @return 题目分页结果
     */
    Page<ProblemTableVO> pageQuery(IPage<ProblemTableVO> page, String keyword, Integer difficulty,
                                  Set<Long> tagIds, ProblemAccessScope scope);

    /**
     * 检查题目是否仍被未结束的非克隆比赛引用
     * @param problemId 原题 ID
     * @param now 当前业务时间
     * @return 是否仍处于比赛专属保护期
     */
    boolean hasActiveContestReference(Long problemId, LocalDateTime now);

    /**
     * 按管理归属和实时可见性筛选题目，在数据库分页前完成过滤
     * @param page 分页参数
     * @param query 筛选条件
     * @param scope 当前用户的管理范围及统一查询时间
     * @return 包含实时可见性的题目分页
     */
    Page<Problem> pageManaged(Page<Problem> page, AdminProblemPageQuery query, ProblemAccessScope scope);

    /**
     * 查询未被活动比赛封题的公开题号
     * @param now 当前业务时间
     * @return 可公开访问的题号
     */
    List<String> listPublicProblemIds(LocalDateTime now);

    /**
     * 统计当前用户可访问的普通题库题目
     * @param scope 服务端生成的访问范围
     * @return 授权范围内题目数
     */
    long countAccessible(ProblemAccessScope scope);

    /**
     * 在分页前过滤不可复用的专属题及已加入目标比赛的题目
     * @param page 分页参数
     * @param query 筛选条件
     * @param contestId 目标比赛 ID
     * @param scope 当前用户的题目访问范围
     * @return 可选题目分页
     */
    Page<Problem> pageAvailableForContest(Page<Problem> page, AdminProblemPageQuery query, Long contestId, ProblemAccessScope scope);

    /**
     * 批量检查是否存在未结束的普通比赛引用
     * @param ids 候选原题 ID，调用方保证非空
     * @param now 当前业务时间
     * @return 是否有任意原题仍处于专属保护期
     */
    boolean hasActiveContestReferences(List<Long> ids, LocalDateTime now);

    /**
     * 批量释放已无有效专属引用的题目，保留管理员手动公开状态
     * @param ids 已锁定的候选原题 ID，调用方保证非空
     * @param now 当前业务时间
     * @return 实际转为私有的题目数量
     */
    int releaseContestProblems(List<Long> ids, LocalDateTime now);
}
