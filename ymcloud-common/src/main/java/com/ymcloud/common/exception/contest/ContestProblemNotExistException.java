package com.ymcloud.common.exception.contest;

/**
 * @PackageName: com.ymcloud.common.exception.contest
 * @ClassName: ContestProblemNotExistException
 * @Author: Yemiao
 * @CreateTime: 2025-08-15  22:34
 * @Description: 比赛题目不存在异常
 */
public class ContestProblemNotExistException extends ContestException{

    public ContestProblemNotExistException() {
        super("该比赛不存在此题目或者已被删除");
    }

}
