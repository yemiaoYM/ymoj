package com.ymcloud.judge.gojudge;

/**
 * v1.13.3 以 HTTP 404 表示文件丢失；上层须重新准备同一任务，沿用有限重试。
 */
public class GoJudgeFileMissingException extends GoJudgeException {

    public GoJudgeFileMissingException() {
        super("go-judge 文件已丢失，需重新准备任务");
    }
}
