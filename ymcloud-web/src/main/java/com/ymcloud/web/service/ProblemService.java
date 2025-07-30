package com.ymcloud.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.ProblemPageQuery;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.pojo.vo.ProblemTableVO;
import com.ymcloud.pojo.vo.ProblemVO;

import java.util.List;

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
     * @param query
     * @return
     */
    PageVO<ProblemTableVO> pageQuery(ProblemPageQuery query);

    /**
     * 根据题目id获取题目信息
     * @param problemId 题目id
     * @return 题目
     */
    ProblemVO getProblemById(String problemId);
}
