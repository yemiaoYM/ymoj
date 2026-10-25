package com.ymcloud.judge.gojudge;

import java.io.IOException;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 沙箱基础设施异常，必须进入系统故障流程，不能作为用户程序的判定依据。
 */
public class GoJudgeException extends IOException {

    /**
     * 标识沙箱基础设施失败，供任务边界有限恢复。
     * @param message 不含私有数据的错误提示
     */
    public GoJudgeException(String message) {
        super(message);
    }
}
