package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ContestProblemTableVO;
import com.ymcloud.pojo.vo.common.PageVO;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  19:09
 * @Description: 管理端比赛服务接口
 */
public interface AdminContestService {

    /**
     * 上传经过内容校验的比赛封面，尚不关联或创建比赛。
     * @param file 裁剪后的公开封面图片
     * @return 统一存储返回的公开地址
     */
    String uploadImage(MultipartFile file);

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
    Contest getContestById(Long id);

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
     * @param password 密码（私有比赛时必填）
     */
    void updateContestVisibility(Long id, Integer visibility, String password);


    /**
     * 克隆比赛
     * @param id 源比赛ID
     * @param title 新比赛标题
     * @param visibility 访问权限
     * @param password 密码（私有比赛时必填）
     */
    void cloneContest(Long id, String title, Integer visibility, String password);

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
     * 批量添加题目到比赛（会将题目权限设置为比赛专用）
     * @param contestId 比赛ID
     * @param problemIds 题目ID列表
     */
    void addProblemsToContest(Long contestId, List<Long> problemIds);

    /**
     * 更新比赛题目信息
     * @param contestId 比赛ID
     * @param problemId 题目ID
     * @param displayId 展示ID（如：A, B, C）
     * @param displayTitle 在比赛中显示的题目标题
     * @param color 气球颜色
     */
    void updateContestProblem(Long contestId, Long problemId, String displayId, String displayTitle, String color);

    /**
     * 从比赛中移除题目（会将题目权限设置为私有）
     * @param contestId 比赛ID
     * @param problemIds 题目ID列表
     */
    void removeContestProblem(Long contestId, List<Long> problemIds);
}
