package com.ymcloud.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.query.ContestPageQuery;
import com.ymcloud.pojo.vo.ContestTableVO;
import com.ymcloud.pojo.vo.PageVO;

/**
 * @PackageName: com.ymcloud.web.service
 * @ClassName: ContestService
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:47
 * @Description: ContestService
 */
public interface ContestService extends IService<Contest> {

    /**
     * 获取比赛列表表单 分页查询
     * @param query
     * @return
     */
    PageVO<ContestTableVO> pageQuery(ContestPageQuery query);
}
