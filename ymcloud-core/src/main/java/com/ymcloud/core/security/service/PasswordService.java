package com.ymcloud.core.security.service;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.exception.user.UserPasswordNotMatchException;
import com.ymcloud.common.exception.user.UserPasswordRetryLimitExceedException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.context.AuthenticationContextHolder;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.core.security.service
 * @ClassName: PasswordService
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  12:10
 * @Description: 处理验证密码
 */
@Component
public class PasswordService {

    @Autowired
    private  RedisCache redisCache;

    /**
     * 最大试错次数
     */
    @Value(value = "${user.password.maxRetryCount}")
    private int maxRetryCount;

    /**
     * 锁定时间
     */
    @Value(value = "${user.password.lockTime}")
    private int lockTime;

    /**
     * 验证密码
     * @param user 当前登录用户
     */
    public void validate(User user) {
        Authentication usernamePasswordAuthenticationToken = AuthenticationContextHolder.getContext();
        String username = usernamePasswordAuthenticationToken.getName();
        String password = usernamePasswordAuthenticationToken.getCredentials().toString();

        //获取当前用户的缓存试错次数
        String key= CacheConstants.PWD_ERR_CNT_KEY + username;
        Integer retryCount = redisCache.getCacheObject(key);
        //如果没有则设定为0
        if (retryCount == null) {
            retryCount = 0;
        }
        //超过最大试错次数
        if (retryCount >= maxRetryCount) {
            throw new UserPasswordRetryLimitExceedException(maxRetryCount, lockTime);
        }
        //校验密码
        if (!matches(user, password)) {
            retryCount = retryCount + 1;
            redisCache.setCacheObject(key, retryCount, lockTime, TimeUnit.MINUTES);
            throw new UserPasswordNotMatchException();
        } else {
            //如果正确则试错次数清零
            redisCache.deleteObject(key);
        }
    }

    /**
     * 在加密情况下校验密码是否正确
     * @param user 当前用户
     * @param password 用户输入密码
     * @return 是否正确
     */
    private boolean matches(User user, String password) {
        return SecurityUtils.matchesPassword(password, user.getPassword());
    }

}
