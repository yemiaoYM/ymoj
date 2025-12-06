package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.vo.oj.ContestProblemTableVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: ContestProblemMapper
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  22:46
 * @Description: ContestProblemMapper
 */
@Mapper
public interface ContestProblemMapper extends BaseMapper<ContestProblem> {

    /**
     * 获取比赛题目列表
     * @param contestId 比赛id
     * @param userId 用户id
     * @return 比赛题目列表
     */
    List<ContestProblemTableVO> getContestProblemList(@Param("contestId") Long contestId, @Param("userId") Long userId);

}
