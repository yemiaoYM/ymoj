package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.vo.PageVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: AdminContestService
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
}
