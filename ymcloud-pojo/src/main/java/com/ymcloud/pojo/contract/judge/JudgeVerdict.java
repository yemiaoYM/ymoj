package com.ymcloud.pojo.contract.judge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 与 OJ 既有状态编号一致的线协议枚举，不依赖数据库枚举处理器。
 */
@Getter
public enum JudgeVerdict {

    STATUS_NOT_SUBMITTED(-10, "Not Submitted"),
    STATUS_CANCELLED(-4, "Cancelled"),
    STATUS_PRESENTATION_ERROR(-3, "Presentation Error"),
    STATUS_COMPILE_ERROR(-2, "Compile Error"),
    STATUS_WRONG_ANSWER(-1, "Wrong Answer"),
    STATUS_ACCEPTED(0, "Accepted"),
    STATUS_TIME_LIMIT_EXCEEDED(1, "Time Limit Exceeded"),
    STATUS_MEMORY_LIMIT_EXCEEDED(2, "Memory Limit Exceeded"),
    STATUS_RUNTIME_ERROR(3, "Runtime Error"),
    STATUS_SYSTEM_ERROR(4, "System Error"),
    STATUS_PENDING(5, "Pending"),
    STATUS_COMPILING(6, "Compiling"),
    STATUS_JUDGING(7, "Judging"),
    STATUS_PARTIAL_ACCEPTED(8, "Partial Accepted"),
    STATUS_SUBMITTING(9, "Submitting"),
    STATUS_SUBMITTED_FAILED(10, "Submitted Failed"),
    STATUS_NULL(15, "No Status");

    private final int code;
    private final String status;

    JudgeVerdict(int code, String status) {
        this.code = code;
        this.status = status;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    @JsonCreator
    public static JudgeVerdict fromCode(int code) {
        for (JudgeVerdict verdict : values()) {
            if (verdict.code == code) {
                return verdict;
            }
        }
        throw new IllegalArgumentException("Unknown judge status: " + code);
    }
}
