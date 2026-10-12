package com.ymcloud.judge.exception;

public class TimeLimitExceededException extends RuntimeException {

    public TimeLimitExceededException() {
        super("代码运行超时");
    }
}
