package com.ymcloud.core.annotation;

import java.lang.annotation.*;

/**
 * @PackageName: com.ymcloud.core.annotation
 * @ClassName: RepeatSubmit
 * @Author: Yemiao
 * @CreateTime: 2025-09-07  00:45
 * @Description: 自定义注解防止重复提交
 */
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {
    /**
     * 间隔时间(ms)，小于此时间视为重复提交
     */
    public int interval() default 10000;

    /**
     * 提示消息
     */
    public String message() default "不允许重复提交，请稍候再试";
}
