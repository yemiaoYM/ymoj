package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.ProblemSource;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;


/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: ProblemEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  20:23
 * @Description: ProblemEntityService
 */
public interface ProblemEntityService extends IService<Problem> {
    /**
     * 查询并锁定当前最大的本地题目展示id
     * @param source 题目来源
     * @return 本地题目展示id
     */
    String selectLatestLocalProblemIdForUpdate(ProblemSource source);

    /**
     * 题单列表分页查询
     * @param page   分页
     * @param keyword  题目/题号 支持模糊查询
     * @param difficulty 难度
     * @param source     来源
     * @return 题单列表
     */
    Page<ProblemTableVO> pageQuery(IPage<ProblemTableVO> page, String keyword, Integer difficulty, String source);
}
