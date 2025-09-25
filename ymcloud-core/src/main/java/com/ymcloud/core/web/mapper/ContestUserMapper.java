package com.ymcloud.core.web.mapper;

import java.util.List;
import com.ymcloud.pojo.vo.oj.ContestCountVO;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.ContestUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: ContestUserMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  01:01
 * @Description: ContestUserMapper
 */
@Mapper
public interface ContestUserMapper extends BaseMapper<ContestUser> {

    /**
     * 根据比赛id列表查询每个比赛的参与人数
     * @param contestIds 比赛id列表
     * @return 每个比赛的参与人数
     */
    List<ContestCountVO> getCountByContestIds(@Param("contestIds") List<Long> contestIds);

}
