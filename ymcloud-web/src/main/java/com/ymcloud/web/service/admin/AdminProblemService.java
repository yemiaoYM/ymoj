package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: AdminProblemService
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:35
 * @Description: 管理端题目服务接口
 */
public interface AdminProblemService {

    /**
     * 分页查询题目列表
     * @param pageQuery 查询条件
     * @return 题目列表
     */
    PageVO<Problem> queryList(AdminProblemPageQuery pageQuery);

    /**
     * 根据ID查询题目详情
     * @param id 题目ID
     * @return 题目信息
     */
    Problem getProblemById(Long id);

    /**
     * 添加题目
     * @param problemDTO 题目信息
     */
    void addProblem(ProblemDTO problemDTO);

    /**
     * 修改题目
     * @param id 题目ID
     * @param problemDTO 题目信息
     */
    void updateProblem(Long id, ProblemDTO problemDTO);

    /**
     * 根据ID删除题目
     * @param ids 题目ID列表
     */
    void deleteProblemById(List<Long> ids);

    /**
     * 修改题目可见性
     * @param id 题目ID
     * @param visibility 可见性：1=公开，2=私有，3=比赛专用
     */
    void updateProblemVisibility(Long id, Integer visibility);
}
