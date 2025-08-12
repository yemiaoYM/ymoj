package com.ymcloud.common.exception.contest;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.contest
 * @ClassName: ContestException
 * @Author: Yemiao
 * @CreateTime: 2025-08-13  01:40
 * @Description: 比赛相关业务异常
 */
public class ContestException extends ServiceException {

    public ContestException(){
        super("比赛业务异常");
    }

    public ContestException(String message) {
        super(message);
    }

    public ContestException(String message, Integer code) {
        super(message, code);
    }

}
