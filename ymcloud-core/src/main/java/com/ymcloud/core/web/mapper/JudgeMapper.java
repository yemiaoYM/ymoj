package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.vo.ProblemCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: JudgeMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  00:33
 * @Description: JudgeMapper
 */
@Mapper
public interface JudgeMapper extends BaseMapper<Judge> {

    /**
     * 批量查询题目的提交情况
     * @param problemIds 题目唯一表示列表
     * @return 题目提交情况列表
     */
    List<ProblemCountVO> getProblemListCount(@Param("problemIds") List<String> problemIds);

}
