package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.vo.ProblemCountVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: JudgeEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-26  00:52
 * @Description: JudgeEntityServiceImpl
 */
@Service
public class JudgeEntityServiceImpl extends ServiceImpl<JudgeMapper, Judge> implements JudgeEntityService {

    /**
     * 批量查询题目的提交情况
     * @param problemIds 题目唯一表示列表
     * @return 题目提交情况列表
     */
    @Override
    public List<ProblemCountVO> getProblemListCount(List<String> problemIds){
        return baseMapper.getProblemListCount(problemIds);
    }

}
