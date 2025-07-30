package com.ymcloud.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.dto.LoginDTO;
import com.ymcloud.pojo.entity.User;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: UserService
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  20:20
 * @Description: UserService
*/
public interface UserService extends IService<User> {

    /**
     * 用户登录
     * @param loginDTO
     * @return
     */
    String login(LoginDTO loginDTO);
}
