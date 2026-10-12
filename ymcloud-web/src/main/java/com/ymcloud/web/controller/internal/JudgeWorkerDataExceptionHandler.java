package com.ymcloud.web.controller.internal;

import com.ymcloud.common.exception.problem.ProblemException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 服务接口使用真实 HTTP 状态，不向 Worker 返回业务异常详情或私有文件内容。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = JudgeWorkerDataController.class)
public class JudgeWorkerDataExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).build();
    }

    @ExceptionHandler(ProblemException.class)
    public ResponseEntity<Void> unavailableVersion(ProblemException exception) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> unavailableService(Exception exception) {
        // 内部客户端只需 HTTP 状态，数据库、路径和存储异常详情不进入响应。
        return ResponseEntity.internalServerError().build();
    }
}
