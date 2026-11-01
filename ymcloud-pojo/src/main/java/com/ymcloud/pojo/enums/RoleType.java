package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-02
 * @Description: 内置角色编码，超级管理员仍仅按用户 ID 识别
 */
@Getter
public enum RoleType {

    USER("USER", "普通用户"),
    PROBLEM_SETTER("PROBLEM_SETTER", "出题人"),
    CONTEST_MANAGER("CONTEST_MANAGER", "比赛管理员"),
    ADMIN("ADMIN", "普通管理员");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    /**
     * 定义内置角色及其业务名称
     * @param code 数据库及安全上下文使用的角色编码
     * @param description 角色名称
     */
    RoleType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
