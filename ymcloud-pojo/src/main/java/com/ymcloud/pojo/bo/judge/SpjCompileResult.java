package com.ymcloud.pojo.bo.judge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-06
 * @Description: 由 Worker 返回的编译状态与有界诊断
 */
@Getter
public final class SpjCompileResult {

    /**
     * 原编译请求的唯一标识
     */
    private final String requestId;

    /**
     * 被编译校验器的源码摘要
     */
    private final String digest;

    /**
     * 编译是否通过
     */
    private final boolean success;

    /**
     * 有界编译诊断
     */
    private final String message;

    /**
     * 创建与原请求对应的编译回复
     * @param requestId 原编译请求的唯一标识
     * @param digest 被编译校验器的源码摘要
     * @param success 编译是否通过
     * @param message 有界编译诊断
     */
    @JsonCreator
    public SpjCompileResult(@JsonProperty("requestId") String requestId,
                            @JsonProperty("digest") String digest,
                            @JsonProperty("success") boolean success,
                            @JsonProperty("message") String message) {
        this.requestId = requestId;
        this.digest = digest;
        this.success = success;
        this.message = message;
    }
}
