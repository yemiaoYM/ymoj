package com.ymcloud.web.service.admin;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 题目、比赛物理删除及关联完整性边界
 */
public interface ResourceDeletionService {

    /**
     * 物理删除无比赛引用且没有在途评测的题目
     * 保留历史 AC 记录及用户成绩
     * 保留文件登记和实际文件，不执行文件清理
     * @param ids 题目 ID
     */
    void deleteProblems(List<Long> ids);

    /**
     * 删除非进行中且没有在途评测的比赛，保留原题
     * 保留文件登记和实际文件，不执行文件清理
     * @param ids 比赛 ID
     */
    void deleteContests(List<Long> ids);
}
