package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  21:53
 * @Description: 文件业务归属和访问规则的分类
 */
@Getter
public enum FileBizType {

    /**
     * 可公开读取的通用文件，包括头像、轮播图和比赛封面等，无需业务 ID
     */
    GENERAL(0, "通用"),

    /**
     * 题面图片、题目文档和附件，业务 ID 为题目 ID
     */
    PROBLEM(1, "题目业务"),

    /**
     * 比赛资料和附件，业务 ID 为比赛 ID
     */
    CONTEST(2, "比赛业务"),

    /**
     * 公告正文图片和附件，业务 ID 为公告 ID
     */
    ANNOUNCEMENT(3, "公告业务"),

    /**
     * 受保护的判题输入、输出和测试数据包，业务 ID 为题目 ID
     */
    TEST_DATA(5, "测试数据");

    /**
     * 文件表与接口共用的业务编码
     */
    @EnumValue
    @JsonValue
    private final int code;

    /**
     * 业务类型名称
     */
    private final String description;

    FileBizType(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
