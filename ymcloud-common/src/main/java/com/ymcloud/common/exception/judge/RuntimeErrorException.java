package com.ymcloud.common.exception.judge;

/**
 * @PackageName: com.ymcloud.common.exception.judge
 * @ClassName: RuntimeErrorException
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:27
 * @Description: 运行错误异常
 */
public class RuntimeErrorException extends SandBoxException{

    public RuntimeErrorException(){
        super("代码运行异常，请检查代码");
    }

    public RuntimeErrorException(String message) {
        super(message);
    }

}
