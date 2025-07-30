package com.ymcloud.core.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ymcloud.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;


/**
 * @PackageName: com.ymcloud.core.web.mapper
 * @ClassName: UserMapper
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  20:14
 * @Description: UserMapper
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
