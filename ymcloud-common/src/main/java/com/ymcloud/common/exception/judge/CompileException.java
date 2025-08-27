package com.ymcloud.common.exception.judge;

/**
 * @PackageName: com.ymcloud.common.exception.judge
 * @ClassName: CompileException
 * @Author: Yemiao
 * @CreateTime: 2025-08-28  00:21
 * @Description: 编译异常
 */
public class CompileException extends SandBoxException{

    public CompileException(){
        super("编译错误，请检查代码");
    }

    public CompileException(String message) {
        super(message);
    }

}
