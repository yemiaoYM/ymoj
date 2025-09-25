package com.ymcloud.web.controller.oj;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.common.result.Result;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.dto.UpdateUserEmailDTO;
import com.ymcloud.pojo.dto.UpdateUserPasswordDTO;
import com.ymcloud.pojo.dto.UpdateUserProfileDTO;
import com.ymcloud.pojo.vo.oj.UserProfileVO;
import com.ymcloud.web.service.oj.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户基本信息
     * @return 用户实体
     */
    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        log.info("获取当前用户基本信息");
        LoginUser loginUser = SecurityUtils.getLoginUser();
        UserProfileVO userVO = BeanUtil.copyProperties(loginUser.getUser(), UserProfileVO.class);
        userVO.setRoles(loginUser.getRoles());
        userVO.setPermissions(loginUser.getPermissions());
        return Result.ok(userVO);
    }

    /**
     * 修改当前用户密码
     * @param userPasswordDTO 修改请求体
     */
    @PutMapping("/update-password")
    public Result<?> updatePassword(@Validated @RequestBody UpdateUserPasswordDTO userPasswordDTO) {
        log.info("修改当前用户密码");
        userService.updatePassword(userPasswordDTO);
        return Result.ok().msg("修改成功,请刷新查看");
    }

    /**
     * 修改当前用户电子邮箱
     * @param userEmailDTO 修改请求体
     */
    @PutMapping("/update-email")
    public Result<?> updateEmail(@Validated @RequestBody UpdateUserEmailDTO userEmailDTO) {
        log.info("修改当前用户电子邮箱");
        userService.updateEmail(userEmailDTO);
        return Result.ok().msg("修改成功,请刷新查看");
    }

    /**
     * 修改当前用户基本信息
     * @param userProfileDTO 修改请求体
     */
    @PutMapping("/update-profile")
    public Result<?> updateProfile(@Validated @RequestBody UpdateUserProfileDTO userProfileDTO){
        log.info("修改当前用户基本信息");
        userService.updateProfile(userProfileDTO);
        return Result.ok().msg("修改成功,请刷新查看");
    }


    /**
     * 用户上传头像
     * @param file 图片文件
     */
    @PostMapping("/upload-avatar")
    public Result<?> uploadAvatar(@RequestParam("file") MultipartFile file){
        log.info("用户上传头像");
        userService.uploadAvatar(file);
        return Result.ok().msg("上传头像成功");
    }


}
