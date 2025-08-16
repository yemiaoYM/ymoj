package com.ymcloud.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: CaptchaVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-21  19:30
 * @Description: 返回验证码
 */
@Data
public class CaptchaVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 唯一标识
     */
    private String uuid;
    /**
     * 验证码图片（Base64）
     */
    private String image;
}
