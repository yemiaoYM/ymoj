package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.UserAcproblem;
import com.ymcloud.pojo.vo.UserAcRankVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: UserAcproblemMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  15:24
 * @Description: UserAcproblemMapper
 */
@Mapper
public interface UserAcproblemMapper extends BaseMapper<UserAcproblem> {

    /**
     * 用户ac数量排名
     * @return 用户ac数量排名列表
     */
    List<UserAcRankVO> selectUserAcRank();

}
