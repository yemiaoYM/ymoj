package com.ymcloud.web.service.oj;

import com.ymcloud.pojo.query.oj.SubmissionPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.SubmissionTableVO;
import com.ymcloud.pojo.vo.oj.SubmissionVO;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: SubmissionService
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  16:02
 * @Description: SubmissionService
 */
public interface SubmissionService {

    /**
     * 获取评测结果列表 分页查询
     * @param query 查询实体
     * @return 评测结果列表
     */
    PageVO<SubmissionTableVO> getPublicSubmissionList(SubmissionPageQuery query);

    /**
     * 查询提交详情
     * @param submissionId 提交id
     * @return 提交详情信息
     */
    SubmissionVO getSubmissionDetail(Long submissionId);

    /**
     * 修改分享权限
     * @param submissionId 提交id
     */
    void updateShare(Long submissionId);
}
