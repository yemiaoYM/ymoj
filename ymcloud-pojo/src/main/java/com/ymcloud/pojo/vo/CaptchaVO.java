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

    private String uuid;
    private String image;
}
