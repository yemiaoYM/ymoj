package com.ymcloud.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.exception.submission.SubmissionException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.JudgeMapper;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.SubmissionPageQuery;
import com.ymcloud.pojo.vo.JudgeCaseVO;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.pojo.vo.SubmissionTableVO;
import com.ymcloud.pojo.vo.SubmissionVO;
import com.ymcloud.web.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Autowired
    private JudgeCaseEntityService judgeCaseEntityService;

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
        //1.构建分页实体
        Page<Judge> page=new Page<>(query.getPageNo(),query.getPageSize());
        //分页条件查询
        Page<Judge> judgePage = judgeEntityService.lambdaQuery()
                //个人提交
                .eq(userId != null, Judge::getUserId, userId)
                .eq(query.getStatus() != null, Judge::getStatus, query.getStatus())
                //一定限制为非比赛提交
                .ne(Judge::getContestId,JudgeConstants.CONTEST_SUBMISSION)
                .like(StrUtil.isNotBlank(query.getProblemKey()), Judge::getDisplayId, query.getProblemKey())
                .like(StrUtil.isNotBlank(query.getProblemKey()), Judge::getProblemTitle, query.getProblemKey())
                .like(StrUtil.isNotBlank(query.getUsername()), Judge::getUsername, query.getUsername())
                .orderByDesc(Judge::getCreatedTime)
                .page(page);
        //返回vo实体
        return PageVO.of(judgePage,judge->{
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
        //检查提交id是否合法
        if(submissionId==null || submissionId<0){
            throw new SubmissionException("提交参数和合法");
        }
        //查询提交记录
        Judge submission = judgeEntityService.lambdaQuery()
                .eq(Judge::getId, submissionId)
                .one();
        if(submission==null){
            throw new SubmissionException("提交记录不存在");
        }
        System.out.println(submission);
        //暂不支持查看比赛提交
        if(submission.getContestId()!=0){
            throw new SubmissionException("暂不支持查看比赛提交");
        }
        //只有编译错误才支持查看错误信息
        if(submission.getStatus()!=JudgeStatus.STATUS_COMPILE_ERROR){
            submission.setErrorMessage(null);
        }
        //个人提交和公开分享才支持查看代码
        User user=SecurityUtils.getUser();
        if(!submission.getUserId().equals(user.getId()) && !submission.getShare()){
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


}
