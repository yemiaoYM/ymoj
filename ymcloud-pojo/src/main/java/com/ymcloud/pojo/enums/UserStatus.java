package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  01:39
 * @Description: 用户状态
 */
@Getter
public enum UserStatus {
    DISABLE(0, "停用"),
    OK(1, "正常");

    @EnumValue
    private final int code;
    @JsonValue
    private final String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
