package com.ymcloud.common.exception.contest;

/**
 * @PackageName: com.ymcloud.common.exception.contest
 * @ClassName: ContestNotExistException
 * @Author: Yemiao
 * @CreateTime: 2025-08-13  01:41
 * @Description: 比赛不存在异常
 */
public class ContestNotExistException extends ContestException{

    public ContestNotExistException() {
        super("比赛不存在或已删除");
    }

}
