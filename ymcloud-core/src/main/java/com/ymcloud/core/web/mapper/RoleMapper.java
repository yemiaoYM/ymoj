package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: RoleMapper
 * @Author: Yemiao
 * @CreateTime: 2025-08-20  17:03
 * @Description: RoleMapper
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    List<String> selectRolesByUserId(@Param("userId") Long userId);

}
