package com.ymcloud.pojo.bo.judge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: 有总截止时间的 SPJ 编译检查消息，不创建正式提交
 */
@Getter
public final class SpjCompileRequest {

    /**
     * 本次编译请求的唯一标识
     */
    private final String requestId;

    /**
     * 请求截止时间，单位为 UTC epoch 毫秒
     */
    private final long deadlineEpochMs;

    /**
     * 待编译的校验器源码快照
     */
    private final SpjProgram program;

    /**
     * 创建有截止时间的编译预检请求
     * @param requestId 本次编译请求的唯一标识
     * @param deadlineEpochMs 请求截止时间，单位为 UTC epoch 毫秒
     * @param program 待编译的校验器源码快照
     */
    @JsonCreator
    public SpjCompileRequest(@JsonProperty("requestId") String requestId,
                             @JsonProperty("deadlineEpochMs") long deadlineEpochMs,
                             @JsonProperty("program") SpjProgram program) {
        this.requestId = requestId;
        this.deadlineEpochMs = deadlineEpochMs;
        this.program = program;
    }
}
