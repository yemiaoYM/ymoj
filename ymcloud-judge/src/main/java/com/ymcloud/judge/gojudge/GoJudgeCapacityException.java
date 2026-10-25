package com.ymcloud.judge.gojudge;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-14
 * @Description: 超出固定部署容量，直接产生明确的 SE 终态，不再重试或截短输入。
 */
public class GoJudgeCapacityException extends GoJudgeException {

    /**
     * 标识部署容量不足，不能误判为用户代码错误。
     * @param message 可向用户展示的容量提示
     */
    public GoJudgeCapacityException(String message) {
        super(message);
    }
}
