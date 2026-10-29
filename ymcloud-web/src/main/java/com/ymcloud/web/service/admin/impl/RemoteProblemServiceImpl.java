package com.ymcloud.web.service.admin.impl;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.pojo.dto.admin.RemoteProblemDTO;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.web.remote.RemoteProblemProvider;
import com.ymcloud.web.remote.RemoteProblemImages;
import com.ymcloud.web.service.admin.RemoteProblemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 按已注册的平台适配器分发单题复制请求
 */
@Service
public class RemoteProblemServiceImpl implements RemoteProblemService {

    @Autowired
    private List<RemoteProblemProvider> providers;

    @Autowired
    private RemoteProblemImages images;

    /**
     * 调用匹配的平台并保存插图，不创建题目或测试数据。
     * @param request 已通过入口结构校验的平台与题号
     * @return 待管理员确认保存的草稿
     */
    @Override
    public RemoteProblemVO fetch(RemoteProblemDTO request) {
        RemoteProblemVO draft = providers.stream()
                .filter(provider -> provider.platform().equals(request.getPlatform()))
                .findFirst()
                .orElseThrow(() -> new ProblemException("暂不支持该题目平台", HttpStatus.BAD_REQUEST))
                .fetch(request.getProblemId());
        // 远程标识可能超出本站展示 ID 契约，留空交由新增页填写，不截断产生碰撞
        if (!draft.getProblemId().matches("[A-Za-z0-9_]{1,15}")) {
            draft.setProblemId("");
        }
        images.importImages(draft);
        return draft;
    }
}
