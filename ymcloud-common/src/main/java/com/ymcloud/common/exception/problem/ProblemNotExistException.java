package com.ymcloud.common.exception.problem;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.problem
 * @ClassName: ProblemNotExistException
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  12:31
 * @Description: 题目不存在或已删除异常
 */
public class ProblemNotExistException extends ServiceException {

    public ProblemNotExistException() {
        super("题目不存在或已删除");
    }
}
