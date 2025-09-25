package com.ymcloud.web.service.oj.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.contest.ContestNotAccessException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.exception.submission.SubmissionException;
import com.ymcloud.common.exception.submission.SubmissionNotExistException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.pojo.entity.*;
import com.ymcloud.pojo.query.oj.SubmissionPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.JudgeCaseVO;
import com.ymcloud.pojo.vo.oj.SubmissionTableVO;
import com.ymcloud.pojo.vo.oj.SubmissionVO;
import com.ymcloud.web.service.oj.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: SubmissionServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  16:02
 * @Description: SubmissionServiceImpl
 */
@Service
public class SubmissionServiceImpl implements SubmissionService {

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private JudgeCaseEntityService judgeCaseEntityService;

    @Autowired
    private ContestEntityService contestEntityService;

    @Autowired
    private ContestUserEntityService contestUserEntityService;

    /**
     * 获取公共评测结果列表 分页查询
     * @param query 查询实体
     * @return 评测结果列表
     */
    @Override
    public PageVO<SubmissionTableVO> getPublicSubmissionList(SubmissionPageQuery query) {
        //先判断是否查看个人提交
        Long userId=null;
        if(query.getIsMine()){
            userId= SecurityUtils.getUserId();
            //查询个人记录不支持模糊匹配用户名
            query.setUsername(null);
        }
        //构建分页实体
        Page<Judge> page=new Page<>(query.getPageNo(),query.getPageSize());
        //分页条件查询
        Page<Judge> judgePage = judgeEntityService.lambdaQuery()
                //个人提交
                .eq(query.getIsMine(), Judge::getUserId, userId)
                .eq(query.getStatus() != null, Judge::getStatus, query.getStatus())
                //一定限制为非比赛提交
                .eq(Judge::getContestId,0L)
                //满足其一即可
                .and(StrUtil.isNotBlank(query.getProblemKey()), wrapper -> wrapper
                    .like(Judge::getDisplayId, query.getProblemKey())
                    .or()
                    .like(Judge::getProblemTitle, query.getProblemKey())
                )
                .like(StrUtil.isNotBlank(query.getUsername()), Judge::getUsername, query.getUsername())
                .orderByDesc(Judge::getCreatedTime)
                .page(page);
        //返回vo实体
        return PageVO.of(judgePage, judge->{
            SubmissionTableVO vo= BeanUtil.copyProperties(judge,SubmissionTableVO.class);
            vo.setSubmissionId(judge.getId());
            return vo;
        });
    }

    /**
     *
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

        // 清空vj信息
        submission.setVjudgeUsername(null);
        submission.setVjudgeSubmitId(null);
        submission.setVjudgePassword(null);

        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        //处理比赛提交
        if(submission.getContestId()!=0){
            //查询比赛
            Contest contest = contestEntityService.getById(submission.getContestId());
            if(contest==null) {
                throw new ContestNotExistException();
            }
            //先验证权限
            if(!contest.getVisibility().equals(ContestConstants.VISIBILITY_PUBLIC)){
                boolean isAccess = contestUserEntityService.lambdaQuery()
                        .eq(ContestUser::getContestId, contest.getId())
                        .eq(ContestUser::getUserId, userId)
                        .exists();
                if(!isAccess){
                    throw new ContestNotAccessException();
                }
            }
            //比赛未结束不允许查看他人提交详情
            LocalDateTime now = LocalDateTime.now();
            if(!userId.equals(submission.getUserId())&&now.isBefore(contest.getEndTime())){
                throw new SubmissionException("比赛结束前禁止查看他人提交详情");
            }
        }

        //个人提交和公开分享才支持查看代码
        if(!submission.getUserId().equals(userId) && !submission.getShare()){
            //不为本人且不公开分享
            submission.setCode(null);
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
        if (judge.getContestId() != 0) {
            throw new SubmissionException("对不起，您不能分享比赛题目的提交代码！", HttpStatus.FORBIDDEN);
        }
        //修改分享权限
        judge.setShare(!judge.getShare());
        boolean isOk = judgeEntityService.updateById(judge);
        if(!isOk){
            throw new SubmissionException("修改代码权限失败！");
        }
    }

}
