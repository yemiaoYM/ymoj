package com.ymcloud.common.exception.problem;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.problem
 * @ClassName: ProblemException
 * @Author: Yemiao
 * @CreateTime: 2025-08-06  00:59
 * @Description: 题目相关异常
 */
public class ProblemException extends ServiceException {

    public ProblemException(){
        super("题目业务异常");
    }

    public ProblemException(String message) {
        super(message);
    }

    public ProblemException(String message, Integer code) {
        super(message, code);
    }

    public ProblemException(String message, Throwable cause) {
        super(message, cause);
    }

}
