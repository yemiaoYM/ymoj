package com.ymcloud.common.exception;

/**
 * @PackageName: com.ymcloud.common.exception
 * @ClassName: MqException
 * @Author: Yemiao
 * @CreateTime: 2025-08-30  17:44
 * @Description: MQ消息队列异常
 */
public class MqException extends ServiceException{

    public MqException(){
        super("消息队列发生异常");
    }

    public MqException(String msg){
        super(msg);
    }

    public MqException(String msg, Throwable cause){
        super(msg, cause);
    }

}
