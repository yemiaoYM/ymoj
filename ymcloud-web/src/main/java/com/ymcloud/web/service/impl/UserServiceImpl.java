package com.ymcloud.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.UserConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.user.*;
import com.ymcloud.common.utils.IpUtil;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.security.context.AuthenticationContextHolder;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.TokenService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.core.web.mapper.UserMapper;
import com.ymcloud.pojo.dto.*;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.entity.UserAcproblem;
import com.ymcloud.pojo.enums.UserSex;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.pojo.vo.UserInfoVO;
import com.ymcloud.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindException;

import java.time.LocalDateTime;
import java.util.List;

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

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private UserAcproblemEntityService userAcproblemEntityService;

    /**
     * 用户登录
     * @param loginDTO 登录请求体
     * @return token
     */
    @Override
    public String login(LoginDTO loginDTO) {
        //1.验证码检测
//        validateCaptcha(loginDTO.getCode(),loginDTO.getUuid());
        //2.信息校验（简化放到DTO的注释检验实现）
        String username=loginDTO.getUsername().trim();
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
     * 用户注册
     * @param registerDTO 注册请求体
     */
    @Override
    public void register(RegisterDTO registerDTO) {
        //1.验证码校验
        validateCaptcha(registerDTO.getCode(),registerDTO.getUuid());
        //2.查询是否信息已被注册
        String username=registerDTO.getUsername().trim();
        String password=registerDTO.getPassword();
        String email=registerDTO.getEmail();
        //2.1用户名也是唯一字段，查询是否已存在
        if(userEntityService.lambdaQuery().eq(User::getUsername,username).exists()){
            throw new UserException("该用户名已被注册");
        }
        //2.2校验email是否被注册过
        if(userEntityService.lambdaQuery().eq(User::getEmail,email).exists()){
            throw new UserException("该邮箱已被其他用户使用");
        }
        //3.生成加密密码
        String encodePassword= SecurityUtils.encryptPassword(password);
        //4.构造User实体
        User user=new User();
        user.setUsername(username);
        user.setPassword(encodePassword);
        user.setEmail(email);
        //5.插入新用户
        boolean save = userEntityService.save(user);
        if(!save){
            throw new UserException("系统注册失败，请稍后再试");
        }
    }

    /**
     * 查询用户信息
     * @param username 用户名
     * @return 用户信息
     */
    @Override
    public UserInfoVO getUserInfo(String username) {
        //1.校验信息
        if(StrUtil.isBlank(username)||username.length()<UserConstants.USERNAME_MIN_LENGTH
                || username.length()>UserConstants.USERNAME_MAX_LENGTH){
            throw new UserException("参数错误", HttpStatus.BAD_REQUEST);
        }
        //2.查询用户基本信息
        User profile = userEntityService.lambdaQuery().eq(User::getUsername, username).one();
        //3.校验用户状态
        if(profile==null||profile.getStatus()== UserStatus.DISABLE){
            throw new UserException("用户不存在或者已停用");
        }
        //4.拷贝前端实体
        UserInfoVO userInfoVO= BeanUtil.copyProperties(profile, UserInfoVO.class);
        //5.查询用户提交情况
        //5.1用户已解决题目
        List<String> list = userAcproblemEntityService.lambdaQuery()
                .select(UserAcproblem::getDisplayId)
                .eq(UserAcproblem::getUsername, username)
                //只查询非比赛提交
                .eq(UserAcproblem::getContestId, 0)
                .list().stream().map(UserAcproblem::getDisplayId).toList();
        userInfoVO.setProblemId(list);
        userInfoVO.setAc(list.size());
        //5.2用户总提交
        Long total = judgeEntityService.lambdaQuery().eq(Judge::getUsername, username).count();
        userInfoVO.setTotal(total.intValue());
        //返回实体
        return userInfoVO;
    }

    /**
     * 当前用户修改密码
     * @param userPasswordDTO 修改请求体
     */
    @Override
    public void updatePassword(UpdateUserPasswordDTO userPasswordDTO) {
        //1.校验信息
        String password=userPasswordDTO.getPassword();
        String newPassword=userPasswordDTO.getNewPassword();
        String rePassword=userPasswordDTO.getRePassword();
        //1.1用户输入的两次新密码不同
        if(!StrUtil.equals(newPassword,rePassword)){
            throw new UserException("两次密码不同，请检查输入");
        }
        //1.2验证当前密码
        LoginUser loginUser=SecurityUtils.getLoginUser();
        User user = loginUser.getUser();
        if (!SecurityUtils.matchesPassword(password,user.getPassword())) {
            throw new UserPasswordNotMatchException();
        }
        if(StrUtil.equals(password,newPassword)){
            throw new UserException("与当前密码一致");
        }
        //2.设置新密码
        String encodePassword= SecurityUtils.encryptPassword(newPassword);
        User updateUser=new User();
        updateUser.setId(user.getId());
        updateUser.setPassword(encodePassword);
        boolean b = userEntityService.updateById(updateUser);
        if(!b){
            throw new UserException("修改密码失败，请稍后再试");
        }
        //3.更新用户缓存
        user.setPassword(encodePassword);
        tokenService.setLoginUser(loginUser);
    }

    /**
     * 修改当前用户电子邮箱
     * @param userEmailDTO 修改请求体
     */
    @Override
    public void updateEmail(UpdateUserEmailDTO userEmailDTO) {
        //1.校验信息
        //1.1获取当前用户信息
        String password=userEmailDTO.getPassword();
        String newEmail=userEmailDTO.getNewEmail();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        User user = loginUser.getUser();
        String email = user.getEmail();
        //1.2校验
        if(StrUtil.equals(newEmail,email)){
           throw new UserException("与当前邮箱一致");
        }
        if(!SecurityUtils.matchesPassword(password,user.getPassword())){
            throw new UserPasswordNotMatchException();
        }
        //2.判断新邮箱是否已被其他用户使用
        if (userEntityService.lambdaQuery().eq(User::getEmail,newEmail).exists()) {
            throw new UserException("当前邮箱已被其他用户使用");
        }
        //3.更新邮箱
        User updateUser=new User();
        updateUser.setId(user.getId());
        updateUser.setEmail(newEmail);
        boolean b = userEntityService.updateById(updateUser);
        if(!b){
            throw new UserException("修改邮箱失败，请稍后再试");
        }
        //4.更新用户缓存
        user.setEmail(email);
        tokenService.setLoginUser(loginUser);
    }

    /**
     * 修改当前用户基本信息
     * @param userProfileDTO 修改请求体
     */
    @Override
    public void updateProfile(UpdateUserProfileDTO userProfileDTO) {
        //1.获取当前用户信息
        LoginUser loginUser = SecurityUtils.getLoginUser();
        User user = loginUser.getUser();
        //2.创建更新用户实体
        User updateUser=new User();
        updateUser.setId(user.getId());
        updateUser.setNickname(userProfileDTO.getNickname());
        updateUser.setSignature(userProfileDTO.getSignature());
        updateUser.setSchool(userProfileDTO.getSchool());
        updateUser.setBlog(userProfileDTO.getBlog());
        updateUser.setSex(userProfileDTO.getSex());
        //3.更新用户基本信息
        boolean b = userEntityService.updateById(updateUser);
        if(!b){
            throw new UserException("修改用户基本信息失败，请稍后再试");
        }
        //4.更新用户缓存
        user.setNickname(userProfileDTO.getNickname());
        user.setSignature(userProfileDTO.getSignature());
        user.setSchool(userProfileDTO.getSchool());
        user.setBlog(userProfileDTO.getBlog());
        user.setSex(userProfileDTO.getSex());
        tokenService.setLoginUser(loginUser);
    }

    /**
     * 校验验证码
     * @param code 验证码
     * @param uuid 唯一标识
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
