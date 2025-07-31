package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @PackageName: com.ymcloud.pojo.enums
 * @ClassName: UserStatus
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  01:39
 * @Description: 用户状态
 */
@Getter
public enum UserStatus {

    OK(1, "正常"), DISABLE(0, "停用");

    @EnumValue
    private final Integer code;
    @JsonValue
    private final String info;

    UserStatus(Integer code, String info)
    {
        this.code = code;
        this.info = info;
    }

}
