package com.ymcloud.common.exception.submission;

/**
 * @PackageName: com.ymcloud.common.exception.submission
 * @ClassName: SubmissionNotExistException
 * @Author: Yemiao
 * @CreateTime: 2025-08-31  15:55
 * @Description: 提交信息不存在异常
 */
public class SubmissionNotExistException extends SubmissionException{

    public SubmissionNotExistException() {
        super("此提交数据不存在！");
    }

}
