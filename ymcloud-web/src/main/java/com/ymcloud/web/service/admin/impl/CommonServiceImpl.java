package com.ymcloud.web.service.admin.impl;

import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.vo.admin.DashboardStatsVO;
import com.ymcloud.pojo.vo.admin.SubmissionChartVO;
import com.ymcloud.web.service.admin.CommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: CommonServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-27  20:45
 * @Description: 管理员公共服务实现类
 */
@Service
public class CommonServiceImpl implements CommonService {

    @Autowired
    private UserEntityService userEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private ContestEntityService contestEntityService;

    @Autowired
    private JudgeEntityService judgeEntityService;

    /**
     * 获取首页统计数据
     * @return 统计数据
     */
    @Override
    public DashboardStatsVO getDashboardStats() {
        DashboardStatsVO stats = new DashboardStatsVO();
        //获取统计数据
        stats.setTotalUsers(userEntityService.count());
        stats.setTotalProblems(problemEntityService.count());
        stats.setTotalContests(contestEntityService.count());
        return stats;
    }

    /**
     * 获取最近两周提交数据折线图
     * @return 折线图数据
     */
    @Override
    public SubmissionChartVO getSubmissionChart() {
        //获取两周前的时间
        LocalDateTime twoWeeksAgo = LocalDateTime.now().minusWeeks(2);
        //查询最近两周的提交记录
        List<Judge> recentSubmissions = judgeEntityService.lambdaQuery()
                .select(Judge::getCreatedTime, Judge::getStatus)
                .ge(Judge::getCreatedTime, twoWeeksAgo)
                //排除比赛提交，只统计普通提交
                .eq(Judge::getContestId, 0L)
                .list();
        //创建日期格式化器
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd");
        //创建日期到提交数据的映射
        Map<String, Long> totalCountMap = new HashMap<>();
        Map<String, Long> acceptedCountMap = new HashMap<>();
        //统计每日的提交数据
        for (Judge submission : recentSubmissions) {
            String dateStr = submission.getCreatedTime().format(dateFormatter);
            //统计总提交数
            totalCountMap.put(dateStr, totalCountMap.getOrDefault(dateStr, 0L) + 1);
            //统计通过数
            if (submission.getStatus() == JudgeStatus.STATUS_ACCEPTED) {
                acceptedCountMap.put(dateStr, acceptedCountMap.getOrDefault(dateStr, 0L) + 1);
            }
        }
        //生成最近14天的日期列表
        List<String> dates = new ArrayList<>();
        List<Long> totalSubmissions = new ArrayList<>();
        List<Long> acceptedSubmissions = new ArrayList<>();
        LocalDateTime currentDate = twoWeeksAgo.toLocalDate().atStartOfDay();
        LocalDateTime endDate = LocalDateTime.now();
        while (currentDate.isBefore(endDate) || currentDate.isEqual(endDate.toLocalDate().atStartOfDay())) {
            String dateStr = currentDate.format(dateFormatter);
            dates.add(dateStr);
            //获取当日的提交数据，如果没有则为0
            totalSubmissions.add(totalCountMap.getOrDefault(dateStr, 0L));
            acceptedSubmissions.add(acceptedCountMap.getOrDefault(dateStr, 0L));
            currentDate = currentDate.plusDays(1);
        }
        //构造返回对象
        SubmissionChartVO chartVO = new SubmissionChartVO();
        chartVO.setDates(dates);
        chartVO.setTotalSubmissions(totalSubmissions);
        chartVO.setAcceptedSubmissions(acceptedSubmissions);
        return chartVO;
    }
}