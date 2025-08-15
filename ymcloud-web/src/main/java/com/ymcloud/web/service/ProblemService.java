package com.ymcloud.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.ProblemPageQuery;
import com.ymcloud.pojo.query.ProblemSubmissionPageQuery;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.pojo.vo.ProblemTableVO;
import com.ymcloud.pojo.vo.ProblemVO;
import com.ymcloud.pojo.vo.SubmissionTableVO;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: ProblemService
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:39
 * @Description: ProblemService
 */
public interface ProblemService extends IService<Problem> {

    /**
     * 获取题目列表表单 分页查询
     * @param query 查询请求
     * @return 题目列表
     */
    PageVO<ProblemTableVO> pageQuery(ProblemPageQuery query);

    /**
     * 获取题目信息
     * @param problemId 题目id
     * @return 题目信息
     */
    ProblemVO getProblemInfo(String problemId);

    /**
     * 随机获取一道题目
     * @return 题目唯一标识
     */
    String randomProblem();

    /**
     * 查询用户当前题目提交列表
     * @param query 分页查询实体
     * @return 提交列表
     */
    PageVO<SubmissionTableVO> problemSubmissionList(ProblemSubmissionPageQuery query);


}
