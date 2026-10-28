package com.ymcloud.common.exception.problem;

import com.ymcloud.common.exception.ServiceException;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-08-06  00:59
 * @Description: 题目相关异常
 */
public class ProblemException extends ServiceException {

    /**
     * 创建未细分的题目业务异常。
     */
    public ProblemException() {
        super("题目业务异常");
    }

    /**
     * 使用业务提示创建题目异常。
     * @param message 可对用户展示的业务提示
     */
    public ProblemException(String message) {
        super(message);
    }

    /**
     * 使用明确错误码创建题目异常。
     * @param message 可对用户展示的业务提示
     * @param code 统一响应中的业务错误码
     */
    public ProblemException(String message, Integer code) {
        super(message, code);
    }

    /**
     * 保留题目操作失败的原始原因。
     * @param message 可对用户展示的业务提示
     * @param cause 安全可保留的原始异常
     */
    public ProblemException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 转换远程题目读取等失败时同时保留错误码和原始原因。
     * @param message 可对用户展示的业务提示
     * @param code 统一响应中的业务错误码
     * @param cause 安全可保留的原始异常
     */
    public ProblemException(String message, Integer code, Throwable cause) {
        super(message, code, cause);
    }

}
