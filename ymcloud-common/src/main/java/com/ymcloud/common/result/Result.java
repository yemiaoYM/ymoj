package com.ymcloud.common.result;

import com.ymcloud.common.constant.HttpStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.common.result
 * @ClassName: Result
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  00:14
 * @Description: 后端统一返回结果
 */
@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    /** 成功 */
    public static final int SUCCESS = HttpStatus.SUCCESS;

    /** 失败 */
    public static final int FAIL = HttpStatus.ERROR;

    private Integer code; //返回状态码
    private String msg;   //错误信息
    private T data;       //数据

    /**
     * 返回成功，无数据
     */
    public static <T> Result<T> ok() {
        return new Result<T>().setCode(SUCCESS).setMsg("操作成功");
    }

    /**
     * 返回成功，附带数据
     */
    public static <T> Result<T> ok(T data) {
        return new Result<T>().setCode(SUCCESS).setMsg("操作成功").setData(data);
    }

    /**
     * 返回失败，无消息
     */
    public static <T> Result<T> fail() {
        return new Result<T>().setCode(FAIL).setMsg("操作失败");
    }

    /**
     * 返回失败，指定消息
     */
    public static <T> Result<T> fail(String msg) {
        return new Result<T>().setCode(FAIL).setMsg(msg);
    }

    /**
     * 返回失败，指定消息和状态码
     */
    public static <T> Result<T> fail(String msg, int code) {
        return new Result<T>().setCode(code).setMsg(msg);
    }

    /**
     * 自定义状态码
     */
    public Result<T> code(Integer code) {
        this.code = code;
        return this;
    }

    /**
     * 自定义提示消息
     */
    public Result<T> msg(String msg) {
        this.msg = msg;
        return this;
    }

    /**
     * 自定义返回数据
     */
    public Result<T> data(T data) {
        this.data = data;
        return this;
    }

}