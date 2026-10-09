package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @PackageName: com.ymcloud.pojo.enums
 * @ClassName: ProblemSource
 * @Author: Yemiao
 * @CreateTime: 2026-08-10  14:00
 * @Description: 题目来源
 */
@Getter
public enum ProblemSource {

    LOCAL("myy", "本地"),
    LUOGU("luogu", "洛谷"),
    ATCODER("atcoder", "AtCoder"),
    CODEFORCES("codeforces", "Codeforces"),
    LEETCODE("leetcode", "LeetCode");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    ProblemSource(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static ProblemSource fromCode(String code) {
        for (ProblemSource source : values()) {
            if (source.code.equals(code)) {
                return source;
            }
        }
        throw new IllegalArgumentException("未知题目来源：" + code);
    }
}
