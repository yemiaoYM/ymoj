package com.ymcloud.web.judge;

import com.ymcloud.common.constant.JudgeConstants;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 持久任务箱状态；评测状态单独保存在 status 字段。
 */
public final class JudgeDispatchState {

    /**
     * 已保存，等待发布或恢复。
     */
    public static final int READY = 0;
    /**
     * 已发布或收到进度，等待后续结果。
     */
    public static final int WAITING = 1;
    /**
     * 已保存终态，不得再发布或覆盖结果。
     */
    public static final int COMPLETED = 2;

    // 保留现有调用名称，策略取值统一来自公共常量，不与任务箱状态互换。
    public static final int FORMAL = JudgeConstants.DEFAULT_JUDGE;
    public static final int SELF_TEST = JudgeConstants.TEST_JUDGE;

    /**
     * 任务箱状态仅作常量使用，不创建实例。
     */
    private JudgeDispatchState() {}
}
