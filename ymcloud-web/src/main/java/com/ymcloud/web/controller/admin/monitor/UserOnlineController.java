package com.ymcloud.web.controller.admin.monitor;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.pojo.vo.admin.UserOnlineVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.admin.monitor
 * @ClassName: UserOnlineController
 * @Author: Yemiao
 * @CreateTime: 2025-09-14  21:23
 * @Description: 在线用户监控
 */
@Slf4j
@RestController
@RequestMapping("/monitor/online")
public class UserOnlineController {

    @Autowired
    private RedisCache redisCache;

    /**
     * 查询在线用户列表
     * @return 在线用户列表
     */
    @PreAuthorize("hasAuthority('monitor:online:list')")
    @GetMapping("/list")
    public Result<List<UserOnlineVO>> list() {
        log.info("查询在线用户列表");
        Collection<String> keys = redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*");
        List<UserOnlineVO> list = keys.stream().map(key -> {
            LoginUser user = redisCache.getCacheObject(key);
            UserOnlineVO userOnlineVO = BeanUtil.copyProperties(user, UserOnlineVO.class);
            userOnlineVO.setUsername(user.getUser().getUsername());
            return userOnlineVO;
        }).toList();
        return Result.ok(list);
    }

    /**
     * 删除在线用户
     * @param token 会话编号
     */
    @PreAuthorize("hasAuthority('monitor:online:forceLogout')")
    @DeleteMapping("/{token}")
    public Result<Void> forceLogout(@PathVariable("token") String token) {
        log.info("删除在线用户: {}", token);
        redisCache.deleteObject(CacheConstants.LOGIN_TOKEN_KEY + token);
        return Result.ok();
    }


}
