package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.vo.ProblemCountVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: JudgeEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  00:52
 * @Description: JudgeEntityService
 */
public interface JudgeEntityService extends IService<Judge> {

    /**
     * 批量查询题目的提交情况
     * @param problemIds 题目唯一表示列表
     * @return 题目提交情况列表
     */
    List<ProblemCountVO> getProblemListCount(List<String> problemIds);
}
