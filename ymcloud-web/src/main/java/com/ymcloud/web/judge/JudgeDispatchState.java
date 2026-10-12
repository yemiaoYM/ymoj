package com.ymcloud.web.judge;

/** 持久任务箱状态；评测状态单独保存在 status 字段。 */
public final class JudgeDispatchState {

    public static final int READY = 0;
    public static final int WAITING = 1;
    public static final int COMPLETED = 2;
    public static final int FORMAL = 0;
    public static final int SELF_TEST = 1;

    private JudgeDispatchState() {
    }
}
