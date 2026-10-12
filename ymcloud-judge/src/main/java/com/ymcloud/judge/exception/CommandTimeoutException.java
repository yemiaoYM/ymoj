package com.ymcloud.judge.exception;

public class CommandTimeoutException extends RuntimeException {

    public CommandTimeoutException() {
        super("沙箱命令超过执行截止时间");
    }
}
