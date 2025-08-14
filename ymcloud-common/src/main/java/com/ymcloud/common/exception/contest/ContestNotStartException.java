package com.ymcloud.common.exception.contest;

/**
 * @PackageName: com.ymcloud.common.exception.contest
 * @ClassName: ContestNotStartException
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  23:27
 * @Description: 比赛还未开始异常
 */
public class ContestNotStartException extends ContestException{

    public ContestNotStartException() {
        super("比赛还未开始，请等待比赛正式开始");
    }

    public ContestNotStartException(String message) {
        super(message);
    }

}
