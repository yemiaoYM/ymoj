package com.ymcloud.web.service.oj;

import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;

/**
 * Web 负责提交权限、持久任务创建和状态读取；执行由独立 Worker 完成。
 */
public interface JudgeService {

    Long submitJudge(JudgeDTO judgeDTO);

    Long testJudge(TestJudgeDTO testJudgeDTO);

    JudgeResultVO getStatus(Long submissionId);
}
