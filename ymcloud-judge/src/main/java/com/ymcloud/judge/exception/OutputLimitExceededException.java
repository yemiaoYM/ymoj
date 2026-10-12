package com.ymcloud.judge.exception;

public class OutputLimitExceededException extends RuntimeException {

    public OutputLimitExceededException() {
        super("程序输出超过大小限制");
    }
}
