package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @PackageName: com.ymcloud.pojo.enums
 * @ClassName: UserSex
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  18:02
 * @Description: 用户性别
 */
@Getter
public enum UserSex {
    UNKNOWN(0, "保密"), MALE(1, "男"), FEMALE(2, "女");

    @EnumValue
    private final int code;
    @JsonValue
    private final String description;

    UserSex(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
