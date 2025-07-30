package com.ymcloud.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * @PackageName: com.ymcloud.pojo.enums
 * @ClassName: JudgeStatus
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  17:29
 * @Description: 评测状态
 */
@Getter
public enum JudgeStatus {

    /**
     * 没有提交
     */
    STATUS_NOT_SUBMITTED(-10, "Not Submitted"),
    /**
     * 取消
     */
    STATUS_CANCELLED(-4, "Cancelled"),
    /**
     * 答案格式错误
     */
    STATUS_PRESENTATION_ERROR(-3, "Presentation Error"),
    /**
     * 编译错误
     */
    STATUS_COMPILE_ERROR(-2, "Compile Error"),
    /**
     * 答案错误
     */
    STATUS_WRONG_ANSWER(-1, "Wrong Answer"),
    /**
     * 答案正确
     */
    STATUS_ACCEPTED(0, "Accepted"),
    /**
     * 时间超限
     */
    STATUS_TIME_LIMIT_EXCEEDED(1, "Time Limit Exceeded"),
    /**
     * 内存超限
     */
    STATUS_MEMORY_LIMIT_EXCEEDED(2, "Memory Limit Exceeded"),
    /**
     * 运行错误
     */
    STATUS_RUNTIME_ERROR(3, "Runtime Error"),
    /**
     * 系统错误
     */
    STATUS_SYSTEM_ERROR(4, "System Error"),
    /**
     * 排队等待评测中
     */
    STATUS_PENDING(5, "Pending"),
    /**
     * 正在编译中
     */
    STATUS_COMPILING(6, "Compiling"),
    /**
     * 判题中
     */
    STATUS_JUDGING(7, "Judging"),
    /**
     * 部分正确
     */
    STATUS_PARTIAL_ACCEPTED(8, "Partial Accepted"),
    /**
     * 提交中
     */
    STATUS_SUBMITTING(9, "Submitting"),
    /**
     * 提交失败
     */
    STATUS_SUBMITTED_FAILED(10, "Submitted Failed"),
    /**
     * 没有提交状态
     */
    STATUS_NULL(15, "No Status");

    @EnumValue
    private final int code;
    @JsonValue
    private final String status;

    JudgeStatus(int code, String status) {
        this.code = code;
        this.status = status;
    }

}
