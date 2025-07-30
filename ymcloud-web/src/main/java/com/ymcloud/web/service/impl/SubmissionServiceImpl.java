package com.ymcloud.web.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.query.SubmissionPageQuery;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.pojo.vo.SubmissionTableVO;
import com.ymcloud.web.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: SubmissionServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  16:02
 * @Description: SubmissionServiceImpl
 */
@Service
public class SubmissionServiceImpl extends ServiceImpl<JudgeMapper, Judge> implements SubmissionService {

    @Autowired
    private JudgeEntityService judgeEntityService;

    /**
     * 获取公共评测结果列表 分页查询
     * @param query 查询实体
     * @return 评测结果列表
     */
    @Override
    public PageVO<SubmissionTableVO> getPublicSubmissionList(SubmissionPageQuery query) {
        //先判断是否查看个人提交
        Long userId=null;
        if(JudgeConstants.PERSONAL_SUBMISSION.equals(query.getScope())){
            userId= SecurityUtils.getUserId();
            //查询个人记录不支持模糊匹配用户名
            query.setUsername(null);
        }
        //1.构建分页实体
        Page<Judge> page=new Page<>(query.getPageNo(),query.getPageSize());
        //分页条件查询
        Page<Judge> judgePage = judgeEntityService.lambdaQuery().eq(userId != null, Judge::getUserId, userId)
                .eq(query.getStatus() != null, Judge::getStatus, query.getStatus())
                //一定限制为非比赛提交
                .ne(Judge::getContestId,JudgeConstants.CONTEST_SUBMISSION)
                .like(StrUtil.isNotBlank(query.getProblemId()), Judge::getDisplayId, query.getProblemId())
                .like(StrUtil.isNotBlank(query.getProblemTitle()), Judge::getProblemTitle, query.getProblemTitle())
                .like(StrUtil.isNotBlank(query.getUsername()), Judge::getUsername, query.getUsername())
                .orderByDesc(Judge::getCreatedTime)
                .page(page);
        //返回vo实体
        return PageVO.of(judgePage,SubmissionTableVO.class);
    }




}
