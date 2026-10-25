package com.ymcloud.judge.exception;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 携带受限的用户代码编译诊断。
 */
public class CompileException extends RuntimeException {

    /**
     * 保留允许返回给用户的编译诊断。
     * @param message 受限的编译错误提示
     */
    public CompileException(String message) {
        super(message);
    }
}
