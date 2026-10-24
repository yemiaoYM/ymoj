package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.UserAcproblem;
import com.ymcloud.pojo.vo.oj.UserAcRankVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: UserAcproblemEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  15:24
 * @Description: UserAcproblemEntityService
 */
public interface UserAcproblemEntityService extends IService<UserAcproblem> {

    /**
     * 聚合用户已 AC 题目数并排序。
     * @return 用户 AC 排名列表
     */
    List<UserAcRankVO> listUserAcRank();
}
