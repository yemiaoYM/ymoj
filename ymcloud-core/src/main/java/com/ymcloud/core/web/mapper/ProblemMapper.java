package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.vo.ProblemTableVO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;


import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: ProblemMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:40
 * @Description: ProblemMapper
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 题单列表分页查询
     * @param page   分页
     * @param title  题目 支持模糊查询
     * @param difficulty 难度
     * @param source     来源
     * @param tagIds     标签查询，只要题目包含这个子集即可
     * @param size       标签列表的长度，方便直接拿取长度
     * @return
     */
    Page<ProblemTableVO> pageQuery(IPage<ProblemTableVO> page,
            @Param("title")       String title,
            @Param("difficulty")  Integer difficulty,
            @Param("source")      String source,
            @Param("tagIds")      List<Long> tagIds,
            @Param("size")        int size
    );
}
