package com.ymcloud.web.service.oj.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.exception.submission.SubmissionException;
import com.ymcloud.common.exception.submission.SubmissionNotExistException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.pojo.bo.ProblemAccessScope;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.query.oj.SubmissionPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.JudgeCaseVO;
import com.ymcloud.pojo.vo.oj.SubmissionTableVO;
import com.ymcloud.pojo.vo.oj.SubmissionVO;
import com.ymcloud.web.service.oj.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  16:02
 * @Description: SubmissionServiceImpl
 */
@Service
public class SubmissionServiceImpl implements SubmissionService {

    @Autowired
    private ResourceAccessService resourceAccess;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private JudgeCaseEntityService judgeCaseEntityService;

    /**
     * 获取公共评测结果列表 分页查询
     * @param query 查询实体
     * @return 评测结果列表
     */
    @Override
    public PageVO<SubmissionTableVO> getPublicSubmissionList(SubmissionPageQuery query) {
        Page<Judge> page = new Page<>(query.getPageNo(), query.getPageSize());
        ProblemAccessScope scope = resourceAccess.problemScope();
        Page<Judge> judgePage = judgeEntityService.pagePublicSubmissions(page, query, scope);
        //返回vo实体
        return PageVO.of(judgePage, judge->{
            SubmissionTableVO vo= BeanUtil.copyProperties(judge,SubmissionTableVO.class);
            vo.setSubmissionId(judge.getId());
            vo.setCanRejudge(scope.isAdministrator());
            return vo;
        });
    }

    /**
     * 按提交归属、分享状态和比赛权限返回可见的提交详情。
     * @param submissionId 提交id
     * @return 提交详情信息
     */
    @Override
    public SubmissionVO getSubmissionDetail(Long submissionId) {
        //查询提交记录
        Judge submission = judgeEntityService.getById(submissionId);
        if(submission==null){
            throw new SubmissionNotExistException();
        }

        if (!resourceAccess.canReadSubmissionCode(submission)) {
            submission.setCode(null);
            submission.setErrorMessage(null);
        }
        //查询评测样例点
        List<JudgeCase> judgeCases = judgeCaseEntityService.lambdaQuery()
                .eq(JudgeCase::getJudgeId, submission.getId())
                .list();
        List<JudgeCaseVO> judgeCaseVOList = BeanUtil.copyToList(judgeCases, JudgeCaseVO.class);
        //拷贝前端实体
        SubmissionVO submissionVO = BeanUtil.copyProperties(submission, SubmissionVO.class);
        submissionVO.setSubmissionId(submission.getId());
        submissionVO.setJudgeCases(judgeCaseVOList);
        return submissionVO;
    }

    /**
     * 修改分享权限
     * @param submissionId 提交id
     */
    @Override
    public void updateShare(Long submissionId) {
        Judge judge = judgeEntityService.getById(submissionId);
        if(judge==null){
            throw new SubmissionNotExistException();
        }
        //判断该提交是否为当前用户的
        Long userId = SecurityUtils.getUserId();
        if(!judge.getUserId().equals(userId)){
            throw new SubmissionException("对不起，您不能修改他人的代码分享权限！", HttpStatus.FORBIDDEN);
        }
        //如果是比赛提交,不可分享
        if (judge.getContestId() != JudgeConstants.PRACTICE_CONTEST_ID) {
            throw new SubmissionException("对不起，您不能分享比赛题目的提交代码！", HttpStatus.FORBIDDEN);
        }
        resourceAccess.canReadSubmissionCode(judge);
        //修改分享权限
        judge.setShare(!judge.getShare());
        boolean isOk = judgeEntityService.updateById(judge);
        if(!isOk){
            throw new SubmissionException("修改代码权限失败！");
        }
    }

}
