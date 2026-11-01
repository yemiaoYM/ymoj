package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.CloneContestDTO;
import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.AdminContestVO;
import com.ymcloud.pojo.vo.admin.ContestProblemTableVO;
import com.ymcloud.pojo.vo.common.PageVO;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  19:09
 * @Description: 管理端比赛服务接口
 */
public interface AdminContestService {

    /**
     * 分页查询比赛列表
     * @param pageQuery 查询条件
     * @return 比赛列表
     */
    PageVO<Contest> queryList(AdminContestPageQuery pageQuery);

    /**
     * 根据ID查询比赛详情
     * @param id 比赛ID
     * @return 比赛信息
     */
    AdminContestVO getContestById(Long id);

    /**
     * 添加比赛
     * @param contestDTO 比赛信息
     */
    void addContest(ContestDTO contestDTO);

    /**
     * 更新比赛
     * @param id 比赛ID
     * @param contestDTO 比赛信息
     */
    void updateContest(Long id, ContestDTO contestDTO);

    /**
     * 根据ID删除比赛
     * @param ids 比赛ID列表
     */
    void deleteContestById(List<Long> ids);

    /**
     * 更新比赛可见性
     * @param id 比赛ID
     * @param visibility 可见性
     * @param password 密码（密码比赛时必填）
     */
    void updateContestVisibility(Long id, Integer visibility, String password);

    /**
     * 克隆比赛
     * @param id 源比赛ID
     * @param request 新比赛标题、访问类型、密码和时间
     */
    void cloneContest(Long id, CloneContestDTO request);

    /**
     * 获取比赛题目列表
     * @param contestId 比赛ID
     * @return 比赛题目列表
     */
    List<ContestProblemTableVO> getContestProblems(Long contestId);

    /**
     * 获取可添加到比赛的题目列表（包括公开和私有题目）
     * @param contestId 比赛ID
     * @param pageQuery 查询条件
     * @return 可添加的题目列表
     */
    PageVO<Problem> getAvailableProblems(Long contestId, AdminProblemPageQuery pageQuery);

    /**
     * 批量添加题目
     * 仅普通比赛触发原题专属状态
     * @param contestId 比赛ID
     * @param problemIds 题目ID列表
     */
    void addProblemsToContest(Long contestId, List<Long> problemIds);

    /**
     * 更新比赛题目的标题和气球颜色，展示 ID 仅由调整顺序功能管理
     * @param contestId 比赛ID
     * @param problemId 比赛题目关联 ID
     * @param displayTitle 在比赛中显示的题目标题
     * @param color 气球颜色
     */
    void updateContestProblem(Long contestId, Long problemId, String displayTitle, String color);

    /**
     * 从比赛中移除题目
     * 仅普通比赛按剩余引用释放专属状态
     * @param contestId 比赛ID
     * @param problemIds 题目ID列表
     */
    void removeContestProblem(Long contestId, List<Long> problemIds);

    /**
     * 保存完整题目顺序，按 A 起连续编号，仅限未开始且无提交的比赛
     * @param contestId 比赛 ID
     * @param ids 按目标顺序排列的比赛题目关联 ID
     */
    void reorderProblems(Long contestId, List<Long> ids);
}
