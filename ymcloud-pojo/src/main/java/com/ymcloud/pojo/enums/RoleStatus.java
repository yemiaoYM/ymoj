package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-02
 * @Description: 角色启停状态，保持现有数据库和接口数值编码
 */
@Getter
public enum RoleStatus {

    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    @EnumValue
    @JsonValue
    private final int code;

    private final String description;

    /**
     * 定义角色启停状态
     * @param code 数据库及接口使用的状态编码
     * @param description 状态名称
     */
    RoleStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
