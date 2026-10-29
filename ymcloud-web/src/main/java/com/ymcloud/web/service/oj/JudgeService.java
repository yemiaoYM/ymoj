package com.ymcloud.web.service.oj;

import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.dto.SubmissionStatusDTO;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: Web 负责提交权限、持久任务创建和状态读取，执行由独立 Worker 完成
 */
public interface JudgeService {

    Long submitJudge(JudgeDTO judgeDTO);

    Long testJudge(TestJudgeDTO testJudgeDTO);

    JudgeResultVO getStatus(Long submissionId);

    /**
     * 按当前配置重新评测原提交，重复或过期命令不再创建任务
     * @param submissionId 原正式提交编号，包含普通和比赛提交
     * @param executionId 预期执行标识，历史提交可为 null
     */
    void rejudge(Long submissionId, String executionId);

    /**
     * 超级管理员修改最终状态并阻止原执行继续写入
     * @param submissionId 原正式提交编号
     * @param request 已校验的状态及部分分数
     */
    void updateSubmissionStatus(Long submissionId, SubmissionStatusDTO request);
}
