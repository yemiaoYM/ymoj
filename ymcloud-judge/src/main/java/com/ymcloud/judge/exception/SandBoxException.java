package com.ymcloud.judge.exception;

/**
 * 沙箱基础设施故障，与用户代码错误区分。
 */
public class SandBoxException extends RuntimeException {

    public SandBoxException(String message) {
        super(message);
    }

    public SandBoxException(String message, Throwable cause) {
        super(message, cause);
    }
}
