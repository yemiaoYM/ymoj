package com.ymcloud.common.exception.submission;

import com.ymcloud.common.exception.ServiceException;

/**
 * @PackageName: com.ymcloud.common.exception.submission
 * @ClassName: Submission
 * @Author: Yemiao
 * @CreateTime: 2025-08-04  15:27
 * @Description: 提交相关异常
 */
public class SubmissionException extends ServiceException {

    public SubmissionException() {
        super("提交异常");
    }

    public SubmissionException(String message) {
        super(message);
    }

    public SubmissionException(String message, Integer code) {
        super(message, code);
    }

    public SubmissionException(String message, Throwable cause) {
        super(message, cause);
    }

}
