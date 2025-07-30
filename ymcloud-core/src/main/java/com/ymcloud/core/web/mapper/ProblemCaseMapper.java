package com.ymcloud.core.web.mapper;
import java.util.List;

import com.ymcloud.pojo.vo.ProblemCaseVO;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.ProblemCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: ProblemCaseMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  14:37
 * @Description: ProblemCaseMapper
 */
@Mapper
public interface ProblemCaseMapper extends BaseMapper<ProblemCase> {

    /**
     * 根据题目id和是否用作题面样例查询样例列表
     * @param problemId 题目id
     * @param isSample 是否用作题面样例
     * @return 样例列表
     */
    @Select("select input,output,note from problem_case where problem_id=#{problemId} and is_sample=#{isSample}")
    List<ProblemCaseVO> getByProblemIdAndIsSample(@Param("problemId") Long problemId, @Param("isSample") Integer isSample);
}
