package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @PackageName: com.ymcloud.pojo.enums
 * @ClassName: FileBizType
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  21:53
 * @Description: 文件业务类型
 */
@Getter
public enum FileBizType {

    GENERAL(0, "通用"),
    PROBLEM_ATTACHMENT(1, "题目附件"),
    PROBLEM_DATA(2, "题目数据包"),
    CONTEST_ATTACHMENT(3, "比赛附件"),
    ANNOUNCEMENT_ATTACHMENT(4, "公告附件"),
    IMAGE(5, "图像图片");

    @EnumValue
    private final int code;

    @JsonValue
    private final String description;

    FileBizType(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
