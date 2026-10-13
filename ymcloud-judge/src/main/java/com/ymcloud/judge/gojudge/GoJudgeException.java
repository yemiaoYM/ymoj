package com.ymcloud.judge.gojudge;

import java.io.IOException;

/**
 * 沙箱基础设施异常，必须进入系统故障流程，不能作为用户程序的判定依据。
 */
public class GoJudgeException extends IOException {

    public GoJudgeException(String message) {
        super(message);
    }
}
