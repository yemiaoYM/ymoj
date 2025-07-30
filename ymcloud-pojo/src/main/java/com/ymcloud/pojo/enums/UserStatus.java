package com.ymcloud.pojo.enums;

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

    OK(1, "正常"), DISABLE(0, "停用"), DELETED(2, "删除");

    private final Integer code;
    private final String info;

    UserStatus(Integer code, String info)
    {
        this.code = code;
        this.info = info;
    }

}
