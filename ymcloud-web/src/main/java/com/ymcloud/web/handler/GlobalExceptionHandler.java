package com.ymcloud.web.handler;

import cn.hutool.core.util.StrUtil;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.result.Result;
import com.ymcloud.common.utils.ExceptionLogUtils;
import com.ymcloud.web.controller.common.FileController;
import com.ymcloud.web.controller.internal.JudgeWorkerDataController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-20  00:41
 * @Description: 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 返回权限拒绝，不为预期拒绝记录业务日志
     * @param e 权限异常
     * @param request 当前请求
     * @param response 当前 HTTP 响应
     * @return 仅含安全提示与 403 业务码的响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request, HttpServletResponse response) {
        setFileErrorStatus(request, response, HttpStatus.FORBIDDEN);
        return Result.fail().code(HttpStatus.FORBIDDEN).msg("访问权限不足");
    }

    /**
     * 返回资源不存在，不输出内部资源解析信息
     * @param e 资源查找异常
     * @param request 当前请求
     * @return 仅含安全提示与 404 业务码的响应
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Result<?> handleNotFound(Exception e, HttpServletRequest request) {
        return Result.fail().code(HttpStatus.NOT_FOUND).msg("资源未找到");
    }

    /**
     * 返回请求方法不支持的安全提示
     * @param e 方法不匹配异常
     * @param request 当前请求
     * @return 含 405 业务码的响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return Result.fail().code(HttpStatus.BAD_METHOD).msg("请求方式不支持");
    }

    /**
     * 返回参数类型错误，不回显原始请求值
     * @param e 类型绑定异常
     * @param request 当前请求
     * @param response 当前 HTTP 响应
     * @return 含 400 业务码的响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request, HttpServletResponse response) {
        setFileErrorStatus(request, response, HttpStatus.BAD_REQUEST);
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg("参数类型不匹配");
    }

    /**
     * 返回约束校验提示，不序列化绑定异常或被拒绝值
     * @param e 已包含约束错误的绑定异常
     * @return 含安全校验提示与 400 业务码的响应
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String message = e.getAllErrors().get(0).getDefaultMessage();
        // 约束提示过长时使用固定参数错误消息
        if (StrUtil.isNotBlank(message) && message.length() > 20) {
            message = "参数格式错误";
        }
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg(message);
    }

    /**
     * 处理缺失请求参数等 Servlet 绑定错误，内部参数异常交给系统边界
     * @param e 请求绑定异常
     * @param request 当前请求
     * @return 含 400 业务码的响应
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public Result<?> handleRequestBinding(ServletRequestBindingException e, HttpServletRequest request) {
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg("参数格式错误");
    }

    /**
     * 拒绝无法读取的请求体，不输出可能含凭据的解析器消息
     * @param e 请求体解析异常
     * @param request 当前请求
     * @return 含固定安全提示的参数错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg("请求参数格式不正确，请检查输入数据");
    }

    /**
     * 按上传限制类型返回简短提示，不输出 multipart 原始异常正文
     * @param e 上传解析异常
     * @param request 当前请求
     * @return 文件数量、大小或请求格式错误响应
     */
    @ExceptionHandler(MultipartException.class)
    public Result<?> handleMultipartException(MultipartException e, HttpServletRequest request) {
        String rootCause = NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName();

        String message = "上传请求格式不正确";
        if ("FileCountLimitExceededException".equals(rootCause)) {
            message = "单次上传的文件数量超过限制";
        } else if (e instanceof MaxUploadSizeExceededException || rootCause.contains("SizeLimit")) {
            return Result.fail().code(HttpStatus.PAYLOAD_TOO_LARGE).msg("上传文件超过大小限制");
        }
        return Result.fail().code(HttpStatus.BAD_REQUEST).msg(message);
    }

    /**
     * 返回领域异常的安全消息，系统故障仅在此记录一次脱敏诊断
     * @param e 保留原始原因的领域异常
     * @param request 当前请求
     * @param response 当前 HTTP 响应
     * @return 仅包含业务错误码与安全消息的响应，不包含异常链
     */
    @ExceptionHandler(ServiceException.class)
    public Result<?> handleServiceException(ServiceException e, HttpServletRequest request, HttpServletResponse response) {
        setFileErrorStatus(request, response, e.getCode());
        if (e.getCode() >= HttpStatus.ERROR && e.getCause() != null) {
            log.error("业务操作失败 uri={} code={}", request.getRequestURI(), e.getCode(), ExceptionLogUtils.forLogging(e));
        }
        return Result.fail().code(e.getCode()).msg(e.getMessage());
    }

    /**
     * 在最终 HTTP 边界记录系统根因类型与原始调用栈
     * @param e 未由业务边界处理的系统异常
     * @param request 当前请求
     * @param response 当前 HTTP 响应
     * @return 不包含系统内部细节的固定失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        setFileErrorStatus(request, response, HttpStatus.ERROR);
        log.error("系统请求失败 uri={} code={}", request.getRequestURI(), HttpStatus.ERROR, ExceptionLogUtils.forLogging(e));
        return Result.fail().code(HttpStatus.ERROR).msg("系统繁忙，请稍后再试");
    }

    /**
     * 文件流请求通过 HTTP 状态区分错误与下载内容，并禁止缓存失败响应
     * @param request 当前请求
     * @param response 当前 HTTP 响应
     * @param code 异常对应的状态码
     */
    private void setFileErrorStatus(HttpServletRequest request, HttpServletResponse response, int code) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        boolean problemCaseRequest = pattern instanceof String route && route.startsWith("/admin/problem/{problemId}/testcases");
        if (handler instanceof HandlerMethod method
                && (FileController.class.isAssignableFrom(method.getBeanType())
                || JudgeWorkerDataController.class.isAssignableFrom(method.getBeanType())
                || problemCaseRequest)) {
            response.setStatus(code);
            response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        }
    }
}
