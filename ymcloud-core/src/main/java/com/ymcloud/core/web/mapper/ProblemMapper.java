package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:40
 * @Description: ProblemMapper
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 题单列表分页查询
     * @param page   分页
     * @param keyword  题目/题号 支持模糊查询
     * @param difficulty 难度
     * @param source     来源
     * @return 题单列表
     */
    Page<ProblemTableVO> pageQuery(
            IPage<ProblemTableVO> page,
            @Param("keyword")     String keyword,
            @Param("difficulty")  Integer difficulty,
            @Param("source")      String source
    );
}
