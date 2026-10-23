package com.ymcloud.core.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.result.Result;
import com.ymcloud.core.annotation.RepeatSubmit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * @PackageName: com.ymcloud.core.interceptor
 * @ClassName: RepeatSubmitInterceptor
 * @Author: Yemiao
 * @CreateTime: 2025-09-07  00:47
 * @Description: 防止重复提交拦截器
 */
@Component
public abstract class RepeatSubmitInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod handlerMethod) {
            Method method = handlerMethod.getMethod();
            RepeatSubmit annotation = method.getAnnotation(RepeatSubmit.class);
            if (annotation != null) {
                if (this.isRepeatSubmit(request, annotation)) {
                    String data = JSON.toJSONString(Result.fail().msg(annotation.message()));
                    response.setStatus(HttpStatus.SUCCESS);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("utf-8");
                    response.getWriter().print(data);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 验证是否重复提交由子类实现具体的防重复提交的规则
     *
     * @param request 请求信息
     * @param annotation 防重复注解参数
     * @return 结果
     */
    public abstract boolean isRepeatSubmit(HttpServletRequest request, RepeatSubmit annotation);
}
