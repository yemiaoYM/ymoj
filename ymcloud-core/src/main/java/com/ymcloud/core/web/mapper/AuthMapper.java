package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.Auth;
import com.ymcloud.pojo.vo.AuthVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: AuthMapper
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  17:06
 * @Description: AuthMapper
 */
@Mapper
public interface AuthMapper extends BaseMapper<Auth> {

    List<AuthVO> selectPermissionsByUserId(@Param("userId") Long userId);

}
