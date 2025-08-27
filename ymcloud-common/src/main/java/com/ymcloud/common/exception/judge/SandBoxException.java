package com.ymcloud.common.exception.judge;

/**
 * @PackageName: com.ymcloud.common.exception.judge
 * @ClassName: SandBoxException
 * @Author: Yemiao
 * @CreateTime: 2025-08-09  22:33
 * @Description: 沙箱运行异常
 */
public class SandBoxException extends JudgeException {

    public SandBoxException() {
        super("对不起，沙箱运行异常，请联系管理员");
    }

    public SandBoxException(String message) {
        super(message);
    }

    public SandBoxException(String message, Throwable cause) {
        super(message, cause);
    }
}
