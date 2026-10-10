package com.ymcloud.web.handler;

import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * @PackageName: com.ymcloud.web.handler
 * @ClassName: GlobalException
 * @Author: Yemiao
 * @CreateTime: 2025-07-20  00:41
 * @Description: 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 权限异常 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址:{},权限校验失败:{}", requestURI, e.getMessage());
        return Result.fail().code(HttpStatus.FORBIDDEN).msg("访问权限不足");
    }

    /**
     * 找不到资源 404
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Result<?> handleNotFound(Exception e, HttpServletRequest request) {
        log.warn("资源未找到，请求URI: {}, 异常信息: {}", request.getRequestURI(), e.getMessage());
        return Result.fail().code(HttpStatus.NOT_FOUND).msg("资源未找到");
    }

    /**
     * 请求方式不支持 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return Result.fail().code(HttpStatus.BAD_METHOD).msg("请求方式不支持");
    }

    /**
     * 方法参数类型不匹配 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型转换失败，请求URI：{}，参数名：{}，错误信息：{}", request.getRequestURI(), e.getName(), e.getMessage());
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg("参数类型不匹配");
    }

    /**
     * 处理自定义参数格式错误或者自定义验证异常 400
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String message = e.getAllErrors().get(0).getDefaultMessage();
        log.warn("参数绑定失败：{}", message);
        //防止返回给前端太长的详细错误
        if (StrUtil.isNotBlank(message) && message.length() > 20) {
            message = "参数格式错误";
        }
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg(message);
    }

    /**
     * 捕获SpringMVC在参数绑定无法获取参数名称异常 400
     */
    @ExceptionHandler({IllegalArgumentException.class, ServletRequestBindingException.class})
    public Result<?> handleIllegalArgument(Exception e, HttpServletRequest request) {
        log.error("参数绑定失败，请求URI：{}，异常信息：{}", request.getRequestURI(), e.getMessage());
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg("参数格式错误");
    }

    /**
     * 请求体格式错误或缺失 400
     */
    @ExceptionHandler(HttpMessageConversionException.class)
    public Result<?> handleHttpMessageNotReadable(HttpMessageConversionException e, HttpServletRequest request) {
        // JSON 解析异常可能包含原始密码或存储密钥，只记录异常类型。
        log.warn("请求体无法读取，请求URI：{}，异常类型：{}", request.getRequestURI(), e.getClass().getSimpleName());
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg("请求参数格式不正确，请检查输入数据");
    }

    /**
     * multipart请求解析失败（文件数量、大小超限或请求不完整）
     */
    @ExceptionHandler(MultipartException.class)
    public Result<?> handleMultipartException(MultipartException e, HttpServletRequest request) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String rootCause = cause.getClass().getSimpleName();
        log.warn("文件上传解析失败，请求URI：{}，根因：{}，异常信息：{}",
                request.getRequestURI(), rootCause, cause.getMessage());

        String message = "上传请求解析失败，请检查文件大小后重试";
        if ("FileCountLimitExceededException".equals(rootCause)) {
            message = "单次上传的文件数量过多，系统将自动分批，请重新选择文件上传";
        } else if (rootCause.contains("SizeLimit") || rootCause.contains("FileSizeLimit")) {
            message = "上传文件超过大小限制，单个文件不能超过50MB";
        }
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg(message);
    }

    /**
     * 捕获业务逻辑异常 默认为500
     */
    @ExceptionHandler(ServiceException.class)
    public Result<?> handleServiceException(ServiceException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.info("业务逻辑异常，uri:{}，异常码：{}，异常信息:{}", requestURI, e.getCode(), e.getMessage());
        return Result.fail().code(e.getCode()).msg(e.getMessage());
    }

    /**
     * 捕获所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("系统异常，请求URI：{}，异常信息：{}", requestURI, e.getMessage(),e);
        return Result.fail().code(HttpStatus.ERROR).msg("系统繁忙，请稍后再试");
    }


}
