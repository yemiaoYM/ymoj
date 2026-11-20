package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.vo.admin.DashboardStatsVO;
import com.ymcloud.pojo.vo.admin.SubmissionChartVO;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: CommonService
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  20:45
 * @Description: 管理员公共服务接口
 */
public interface CommonService {

    /**
     * 获取首页统计数据
     * @return 统计数据
     */
    DashboardStatsVO getDashboardStats();

    /**
     * 获取最近两周提交数据折线图
     * @return 折线图数据
     */
    SubmissionChartVO getSubmissionChart();
}
