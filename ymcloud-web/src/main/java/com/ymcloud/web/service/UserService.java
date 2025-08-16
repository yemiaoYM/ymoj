package com.ymcloud.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.dto.*;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.vo.UserCalendarHeatmapVO;
import com.ymcloud.pojo.vo.UserInfoVO;
import org.springframework.web.multipart.MultipartFile;

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
     * @param loginDTO 登录请求体
     * @return token
     */
    String login(LoginDTO loginDTO);

    /**
     * 用户注册
     * @param registerDTO 注册请求体
     */
    void register(RegisterDTO registerDTO);

    /**
     * 查询用户信息
     * @param username 用户名
     * @return 用户信息
     */
    UserInfoVO getUserInfo(String username);

    /**
     * 当前用户修改密码
     * @param userPasswordDTO 修改请求体
     */
    void updatePassword(UpdateUserPasswordDTO userPasswordDTO);

    /**
     * 修改当前用户电子邮箱
     * @param userEmailDTO 修改请求体
     */
    void updateEmail(UpdateUserEmailDTO userEmailDTO);

    /**
     * 修改当前用户基本信息
     * @param userProfileDTO 修改请求体
     */
    void updateProfile(UpdateUserProfileDTO userProfileDTO);

    /**
     * 获取用户最近一年的提交热力图数据
     * @param username 用户名
     * @return 提交热力图数据
     */
    UserCalendarHeatmapVO getUserCalendarHeatmap(String username);

    /**
     * 用户上传头像
     * @param file 头像文件
     */
    void uploadAvatar(MultipartFile file);

    /**
     * 获取忘记密码用户的邮箱验证码
     * @param forgetPasswordDTO 请求体
     * @return 验证码的唯一标识
     */
    String getForgetCode(ForgetPasswordDTO forgetPasswordDTO);
}
