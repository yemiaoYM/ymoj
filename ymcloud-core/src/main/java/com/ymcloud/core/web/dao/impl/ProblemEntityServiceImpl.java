package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.mapper.ProblemMapper;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  20:23
 * @Description: 题目实体访问与分页查询实现
 */
@Service
public class ProblemEntityServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemEntityService {

    /**
     * 题单列表分页查询
     * @param page   分页
     * @param keyword  题目/题号 支持模糊查询
     * @param difficulty 难度
     * @param tagIds 必须同时包含的标签 ID，空集合表示不筛选
     * @return 题目分页结果
     */
    @Override
    public Page<ProblemTableVO> pageQuery(IPage<ProblemTableVO> page, String keyword, Integer difficulty,
                                        Set<Long> tagIds) {
        return getBaseMapper().pageQuery(page, keyword, difficulty, tagIds);
    }

}
