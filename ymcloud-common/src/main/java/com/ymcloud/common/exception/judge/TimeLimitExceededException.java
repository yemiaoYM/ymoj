package com.ymcloud.common.exception.judge;

/**
 * @PackageName: com.ymcloud.common.exception.judge
 * @ClassName: TimeLimitExceededException
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:23
 * @Description: 代码运行超时异常
 */
public class TimeLimitExceededException extends SandBoxException{

    public TimeLimitExceededException() {
        super("代码运行超时，请优化算法");
    }

    public TimeLimitExceededException(String message) {
        super(message);
    }

}
