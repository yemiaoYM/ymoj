package com.ymcloud.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.user.*;
import com.ymcloud.common.utils.IpUtil;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.context.AuthenticationContextHolder;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.core.web.mapper.UserMapper;
import com.ymcloud.pojo.dto.LoginDTO;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: UserServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  20:20
 * @Description: UserServiceImpl
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private UserEntityService userEntityService;

    /**
     * 用户登录
     * @param loginDTO
     * @return
     */
    @Override
    public String login(LoginDTO loginDTO) {
        //1.验证码检测
        validateCaptcha(loginDTO.getCode(),loginDTO.getUuid());
        //2.信息校验（简化放到DTO的注释检验实现）
        String username=loginDTO.getUsername();
        String password=loginDTO.getPassword();
        //TODO 黑名单校验

        Authentication authentication=null;
        //3.登录校验
        try {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            //将验证信息先存放在当前线程中，为了后续密码校验获取当前验证信息
            AuthenticationContextHolder.setContext(authenticationToken);
            authentication = authenticationManager.authenticate(authenticationToken);
        }catch (AuthenticationException e) {
            throw new UserException(e.getMessage());
        }catch (Exception e){
            throw new ServiceException();
        }finally {
            AuthenticationContextHolder.clearContext();
        }
        LoginUser loginUser=(LoginUser) authentication.getPrincipal();
        Long userId=loginUser.getUser().getId();
        //4.更新登录时间，登录IP等等
        User user=new User();
        user.setId(userId);
        user.setLoginDate(LocalDateTime.now());
        user.setLoginIp(IpUtil.getIpAddr());
        userEntityService.updateById(user);
        //5.根据用户信息生成token
        return tokenService.createToken(loginUser);
    }

    /**
     * 校验验证码
     *
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public void validateCaptcha(String code, String uuid) {
        //1.从缓冲中取出验证码校验
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;
        String captcha = redisCache.getCacheObject(verifyKey);
        if (captcha == null) {
            throw new CaptchaExpireException();
        }
        //2.不管是否验证成功，此验证码已经失效
        redisCache.deleteObject(verifyKey);
        if (!code.equalsIgnoreCase(captcha)){
            throw new CaptchaException();
        }
    }
}
