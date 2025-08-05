package com.ymcloud.common.exception;

import com.ymcloud.common.constant.HttpStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.common.exception
 * @ClassName: ServerException
 * @Author: Yemiao
 * @CreateTime: 2025-07-19  23:36
 * @Description: 业务异常
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer code;
    private final String message;

    public ServiceException() {
        this.code = HttpStatus.ERROR;
        this.message = "服务器异常，请稍后再试";
    }

    public ServiceException(String message) {
        this.code = HttpStatus.ERROR;
        this.message = message;
    }

    public ServiceException(String message, Integer code) {
        this.code = code;
        this.message = message;
    }

    public ServiceException(String message, Throwable cause){
        super(message,cause);
        this.code = HttpStatus.ERROR;
        this.message = message;
    }

    //Throwable cause 在异常类中表示 导致当前异常发生的根本原因（也叫异常链的 原因异常）
    public ServiceException(String message, Integer code, Throwable cause) {
        //将原来的异常给保留,比如：
        //try {
        //  userMapper.insert(user);
        //} catch (SQLException e) {
        //  throw new ServiceException("用户注册失败", 500, e);
        //}
        //可以将原来的SQL异常保留，这样就可以在log中去展示根本异常
        super(message,cause);
        this.code = code;
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

}

