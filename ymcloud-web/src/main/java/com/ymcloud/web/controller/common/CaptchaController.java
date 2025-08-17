package com.ymcloud.web.controller.common;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.util.IdUtil;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.Constants;
import com.ymcloud.common.exception.user.CaptchaGenerateException;
import com.ymcloud.common.result.Result;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.pojo.vo.CaptchaVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.web.controller.common
 * @ClassName: CaptchaController
 * @Author: Yemiao
 * @CreateTime: 2025-07-21  19:27
 * @Description: 验证码操作处理控制器
 */
@RestController
public class CaptchaController {

    @Autowired
    private RedisCache redisCache;

    /**
     * 生成验证码
     */
    @GetMapping("/captchaImage")
    public Result<CaptchaVO> captchaImage() throws CaptchaGenerateException {
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(200, 100, 4, 10);
        //将验证码存储在redis中
        String code = captcha.getCode();
        String uuid= IdUtil.simpleUUID();
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;
        redisCache.setCacheObject(verifyKey,code, Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);
        //返回vo
        String imageBase64Data = captcha.getImageBase64Data();
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setImage(imageBase64Data);
        captchaVO.setUuid(uuid);
        return Result.ok(captchaVO).msg("验证码生成成功");
    }
}
