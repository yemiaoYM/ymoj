package com.ymcloud.common.exception.contest;

/**
 * @PackageName: com.ymcloud.common.exception.contest
 * @ClassName: ContestNotAccessException
 * @Author: Yemiao
 * @CreateTime: 2025-08-14  23:06
 * @Description: 用户没有访问该比赛的权限
 */
public class ContestNotAccessException extends ContestException{

    public ContestNotAccessException() {
        super("对不起，请先到比赛首页输入比赛密码进行注册！");
    }

    public ContestNotAccessException(String message) {
        super(message);
    }

}
