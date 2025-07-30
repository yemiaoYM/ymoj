package com.ymcloud.common.exception.problem;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.problem
 * @ClassName: ProblemPermissionException
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  12:41
 * @Description: 获取题目权限不足异常
 */
public class ProblemPermissionException extends ServiceException {

    public ProblemPermissionException(){
        super("获取题目权限不足");
    }

    public ProblemPermissionException(String message) {
        super(message);
    }
}
