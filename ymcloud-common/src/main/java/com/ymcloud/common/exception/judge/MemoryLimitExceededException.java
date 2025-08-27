package com.ymcloud.common.exception.judge;

/**
 * @PackageName: com.ymcloud.common.exception.judge
 * @ClassName: MemoryLimitExceededException
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:25
 * @Description: 内存超限异常
 */
public class MemoryLimitExceededException extends SandBoxException{

    public MemoryLimitExceededException() {
        super("内存超限，请控制内存占用");
    }

    public MemoryLimitExceededException(String message) {
        super(message);
    }
}
