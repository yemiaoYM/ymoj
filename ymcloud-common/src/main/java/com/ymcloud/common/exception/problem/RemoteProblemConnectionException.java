package com.ymcloud.common.exception.problem;

import com.ymcloud.common.constant.HttpStatus;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 远程题面连接或读取失败，保留原因供平台选择公开替代来源
 */
public class RemoteProblemConnectionException extends ProblemException {

    /**
     * 将网络传输失败转换为题目领域异常。
     * @param message 可展示的连接失败信息
     * @param cause 原始网络异常
     */
    public RemoteProblemConnectionException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
