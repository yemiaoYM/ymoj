package com.ymcloud.web.controller.user;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.common.result.Result;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @PackageName: com.ymcloud.web.controller.user
 * @ClassName: UserController
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  12:49
 * @Description: 用户信息控制器
 */
@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    /**
     * 获取用户基本信息
     * @return 用户实体
     */
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        log.info("获取当前用户基本信息");
        User user = SecurityUtils.getUser();
        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        return Result.ok(userVO);
    }



}
