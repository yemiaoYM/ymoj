package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.RemoteProblemDTO;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 按平台复制公开题面，不保存题目
 */
public interface RemoteProblemService {

    /**
     * 选择平台并读取单道题面的编辑草稿。
     * @param request 平台与题号
     * @return 尚未保存的完整题面草稿
     */
    RemoteProblemVO fetch(RemoteProblemDTO request);
}
