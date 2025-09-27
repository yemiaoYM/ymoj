package com.ymcloud.web.controller.admin.common;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.vo.admin.DashboardStatsVO;
import com.ymcloud.pojo.vo.admin.SubmissionChartVO;
import com.ymcloud.web.service.admin.CommonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @PackageName: com.ymcloud.web.controller.admin.common
 * @ClassName: CommonController
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  20:10
 * @Description: 管理员公共接口控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class CommonController {

    @Autowired
    private CommonService commonService;

    /**
     * 获取首页统计数据
     * @return 统计数据
     */
    @PreAuthorize("hasAnyRole('ADMIN','PROBLEM_ADMIN')")
    @GetMapping("/dashboard/stats")
    public Result<DashboardStatsVO> getDashboardStats() {
        log.info("获取管理员首页统计数据");
        DashboardStatsVO stats = commonService.getDashboardStats();
        return Result.ok(stats);
    }

    /**
     * 获取最近两周提交数据折线图
     * @return 折线图数据
     */
    @PreAuthorize("hasAnyRole('ADMIN','PROBLEM_ADMIN')")
    @GetMapping("/dashboard/submission-chart")
    public Result<SubmissionChartVO> getSubmissionChart() {
        log.info("获取最近两周提交数据折线图");
        SubmissionChartVO chartData = commonService.getSubmissionChart();
        return Result.ok(chartData);
    }
}
