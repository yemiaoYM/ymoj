package com.ymcloud.judge.gojudge;

/**
 * 超出固定部署容量，直接产生明确的 SE 终态，不再重试或截短输入。
 */
public class GoJudgeCapacityException extends GoJudgeException {

    public GoJudgeCapacityException(String message) {
        super(message);
    }
}
