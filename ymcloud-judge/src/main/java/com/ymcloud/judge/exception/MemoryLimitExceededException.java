package com.ymcloud.judge.exception;

public class MemoryLimitExceededException extends RuntimeException {

    public MemoryLimitExceededException() {
        super("代码运行内存超限");
    }
}
