package com.ymcloud.common.exception.judge;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.judge
 * @ClassName: JudgeException
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  20:47
 * @Description: 评测异常
 */
public class JudgeException extends ServiceException {

    public JudgeException(){
        super("评测异常，稍后再试");
    }

    public JudgeException(String message) {
        super(message);
    }

    public JudgeException(String message, Integer code){
        super(message, code);
    }

    public JudgeException(String message, Throwable cause) {
        super(message, cause);
    }

}
