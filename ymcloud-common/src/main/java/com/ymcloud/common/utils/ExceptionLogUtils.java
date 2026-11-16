package com.ymcloud.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.NestedExceptionUtils;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-16
 * @Description: 为故障日志提供不含敏感异常正文的根因类型与原始调用栈
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExceptionLogUtils {

    /**
     * 创建仅供日志使用的根因诊断副本，不修改或替代业务异常链
     * SDK、解析器及数据库异常的正文可能含凭据，因此不复制消息和附加异常
     * @param error 已捕获的实际故障
     * @return 保留根因类型名称与原始调用栈的诊断对象，不用于重新抛出
     */
    public static Throwable forLogging(Throwable error) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(error);
        Throwable diagnostic = new Throwable(cause.getClass().getName());
        diagnostic.setStackTrace(cause.getStackTrace());
        return diagnostic;
    }
}
