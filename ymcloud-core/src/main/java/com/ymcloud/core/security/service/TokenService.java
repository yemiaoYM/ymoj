package com.ymcloud.core.security.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.Constants;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.domain.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.core.security.service
 * @ClassName: TokenService
 * @Author: Yemiao
 * @CreateTime: 2025-07-20  23:57
 * @Description: token验证处理
 */

@Component
@Slf4j
public class TokenService {

    //令牌自定义标识
    @Value("${token.header}")
    private String header;

    //令牌秘钥
    @Value("${token.secret}")
    private String secret;

    //令牌有效期（默认30分钟）
    @Value("${token.expireTime}")
    private int expireTime;

    protected static final long MILLIS_SECOND = 1000;

    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;

    private static final Long MILLIS_MINUTE_TWENTY = 20 * 60 * 1000L;

    @Autowired
    private RedisCache redisCache;

    /**
     * 获取用户身份信息
     * @param request
     * @return
     */
    public LoginUser getLoginUser(HttpServletRequest request) {
        //获取请求携带的令牌
        String token = getToken(request);
        if (StrUtil.isNotEmpty(token)) {
            try {
                Claims claims = parseToken(token);
                //解析对应的权限以及用户信息
                String uuid = (String)claims.get(Constants.LOGIN_USER_KEY);
                String userKey = getTokenKey(uuid);
                return redisCache.getCacheObject(userKey);
            }catch (Exception e) {
                log.error("获取用户信息异常{}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 设置用户身份信息
     * @param loginUser
     */
    public void setLoginUser(LoginUser loginUser) {
        //在窗口期就刷新用户会话
        if (loginUser!=null && StrUtil.isNotEmpty(loginUser.getToken())) {
            refreshToken(loginUser);
        }
    }

    /**
     * 删除用户身份信息
     * @param token
     */
    public void delLoginUser(String token) {
        if (StrUtil.isNotEmpty(token)) {
            String userKey = getTokenKey(token);
            redisCache.deleteObject(userKey);
        }
    }

    /**
     * 创建令牌
     * @param loginUser
     * @return
     */
    public String createToken(LoginUser loginUser) {
        //IdUtil.simpleUUID() 生成32位无连字符的小写UUID字符串 线程安全
        String token = IdUtil.simpleUUID();
        //设置获取用户信息的redis唯一标识键
        loginUser.setToken(token);
        //刷新用户信息
        refreshToken(loginUser);
        //设置token携带的信息
        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, token);
        claims.put(Constants.JWT_USERNAME, loginUser.getUsername());
        return createToken(claims);
    }

    /**
     * 验证令牌有效期，相差不足20分钟，自动刷新缓存，相当于是续费
     * 刷新窗口期
     * 滑动窗口机制
     * @param loginUser
     */
    public void verifyToken(LoginUser loginUser) {
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        if (expireTime - currentTime <= MILLIS_MINUTE_TWENTY) {
            refreshToken(loginUser);
        }
    }

    /**
     * 刷新令牌有效期
     *
     * @param loginUser 登录信息
     */
    public void refreshToken(LoginUser loginUser) {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        //根据uuid将loginUser缓存
        String userKey = getTokenKey(loginUser.getToken());
        //Redis会直接覆盖旧值并重新设置过期时间，Redis 的原生行为，不需要del再set，这样的效率更高
        redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
    }

    /**
     * 从数据声明生成令牌
     * 令牌并没设置过期时间，而是redis控制令牌过期
     *
     * @param claims 数据声明
     * @return 令牌
     */
    private String createToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    /**
     * 从令牌中获取数据声明
     *
     * @param token 令牌
     * @return 数据声明
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从令牌中获取用户名
     * @param token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 获取请求token
     *
     * @param request
     * @return token
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(header);

        /*
        * TOKEN_PREFIX = "Bearer ";
        * 遵循HTTP和OAuth2规范 与主流系统（Spring Security、JWT）默认兼容
        * 提高安全性           区分Authorization类型（如 Basic、Bearer）
        * 便于扩展             后续可以支持多种类型 Token，如 API Key、OAuth Token
        * 通用性强             前后端、第三方平台、API 工具广泛支持
        */

        if (StrUtil.isNotEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX)){
            token = token.replace(Constants.TOKEN_PREFIX, "");
        }
        return token;
    }

    /**
     * 获取用户信息存储在redis中的唯一标识
     * @param uuid
     * @return uuid
     */
    private String getTokenKey(String uuid) {
        return CacheConstants.LOGIN_TOKEN_KEY + uuid;
    }

}
