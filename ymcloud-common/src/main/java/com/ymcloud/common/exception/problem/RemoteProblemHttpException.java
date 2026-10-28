package com.ymcloud.common.exception.problem;

import com.ymcloud.common.constant.HttpStatus;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 保留远程题面 HTTP 状态，供平台适配器区分明确的访问失败
 */
public class RemoteProblemHttpException extends ProblemException {

    private final int remoteStatus;

    /**
     * 将远程非成功响应转换为平台不可用错误，同时保留原始状态。
     * @param message 可向用户展示的平台失败信息
     * @param remoteStatus 远程站点实际返回的 HTTP 状态
     */
    public RemoteProblemHttpException(String message, int remoteStatus) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
        this.remoteStatus = remoteStatus;
    }

    /**
     * 获取远程状态，避免通过提示文本判断恢复路径。
     * @return 远程 HTTP 状态，不是本站业务响应码
     */
    public int getRemoteStatus() {
        return remoteStatus;
    }
}
