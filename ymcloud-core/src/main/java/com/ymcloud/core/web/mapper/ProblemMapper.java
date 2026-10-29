package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:40
 * @Description: 题目数据映射与公开题库筛选
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 题单列表分页查询
     * @param page   分页
     * @param keyword  题目/题号 支持模糊查询
     * @param difficulty 难度
     * @param tagIds 必须同时包含的标签 ID，空集合表示不筛选
     * @return 题目分页结果
     */
    Page<ProblemTableVO> pageQuery(
            IPage<ProblemTableVO> page,
            @Param("keyword")     String keyword,
            @Param("difficulty")  Integer difficulty,
            @Param("tagIds")      Set<Long> tagIds
    );
}
