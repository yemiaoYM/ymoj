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

    /**
     * 根据用户ID查询权限
     * @param userId 用户ID
     * @return 权限列表
     */
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 查询所有权限
     * @return 权限列表
     */
    List<String> selectAllPerms();

    /**
     * 查询所有菜单
     * @return 菜单列表
     */
    List<Auth> selectMenuTreeAll();

    /**
     * 根据用户ID查询菜单
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<Auth> selectMenuTreeByUserId(Long userId);


}
