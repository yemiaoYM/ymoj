package com.ymcloud.judge.gojudge;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: v1.13.3 以 HTTP 404 表示文件丢失；上层须重新准备同一任务，沿用有限重试。
 */
public class GoJudgeFileMissingException extends GoJudgeException {

    /**
     * 标识沙箱文件已不存在，清理时允许按幂等成功处理。
     */
    public GoJudgeFileMissingException() {
        super("go-judge 文件已丢失，需重新准备任务");
    }
}
