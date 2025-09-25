package com.ymcloud.judge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.dto.JudgeDTO;
import com.ymcloud.pojo.dto.TestJudgeDTO;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.vo.oj.JudgeResultVO;

/**
 * @PackageName: com.ymcloud.judge.service
 * @ClassName: JudgeService
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  20:14
 * @Description: JudgeService
 */
public interface JudgeService extends IService<Judge> {

    /**
     * 提交评测
     * @param judgeDTO 评测请求
     * @return 提交编号ID
     */
    Long submitJudge(JudgeDTO judgeDTO);

    /**
     * 自测提交
     * @param testJudgeDTO 评测请求
     * @return 自测临时请求id
     */
    Long testJudge(TestJudgeDTO testJudgeDTO);

    /**
     * 定时评测轮询查询
     * @param submissionId 提交id
     * @return 评测结果
     */
    JudgeResultVO getStatus(Long submissionId);
}
