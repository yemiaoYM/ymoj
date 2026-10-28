package com.ymcloud.web.remote;

import com.ymcloud.pojo.vo.admin.RemoteProblemVO;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 远程平台题面适配契约，隔离各平台协议与统一草稿
 */
public interface RemoteProblemProvider {

    /**
     * 返回平台唯一标识。
     * @return 与前端平台选项一致的标识
     */
    String platform();

    /**
     * 校验平台题号并复制公开题面，不登录、不自动重试。
     * @param problemId 远程平台题号
     * @return 统一题面草稿
     */
    RemoteProblemVO fetch(String problemId);
}
