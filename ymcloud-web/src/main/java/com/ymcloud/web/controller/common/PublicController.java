package com.ymcloud.web.controller.common;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.entity.Announcement;
import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.pojo.query.ContestPageQuery;
import com.ymcloud.pojo.query.ProblemPageQuery;
import com.ymcloud.pojo.query.SubmissionPageQuery;
import com.ymcloud.pojo.vo.*;
import com.ymcloud.web.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.common
 * @ClassName: PublicController
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  18:52
 * @Description: 公共资源控制器
 */
@Slf4j
@RestController
@RequestMapping("/public")
public class PublicController {

    @Autowired
    private UserService userService;

    @Autowired
    private TagService tagService;

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ContestService contestService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private SubmissionService submissionService;

    /**
     * 查询用户信息
     * @param username 用户名
     * @return 用户信息
     */
    @GetMapping("/user-info")
    public Result<UserInfoVO> userInfo(@RequestParam("username") String username) {
        log.info("查询用户 {} 信息",username);
        UserInfoVO userInfo=userService.getUserInfo(username);
        return Result.ok(userInfo);
    }

    /**
     * 获取用户最近一年的提交热力图数据
     * @param username 用户名
     * @return 提交热力图数据
     */
    @GetMapping("/user-calendar-heatmap")
    public Result<UserCalendarHeatmapVO> getUserCalendarHeatmap(@RequestParam("username") String username){
        log.info("获取用户最近一年的提交热力图数据:{}",username);
        UserCalendarHeatmapVO userCalendarHeatmapVO=userService.getUserCalendarHeatmap(username);
        return Result.ok(userCalendarHeatmapVO);
    }

    /**
     * 获取标签列表
     * @return 标签列表
     */
    @Cacheable(cacheNames = CacheConstants.TAG_LIST_KEY)
    @GetMapping("/tag-list")
    public Result<List<Tag>> tagList() {
        log.info("获取标签列表");
        List<Tag> list = tagService.list();
        return Result.ok(list);
    }

    /**
     * 获取题目列表表单 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @GetMapping("/problem-list")
    public Result<PageVO<ProblemTableVO>> problemList(@Validated ProblemPageQuery query) {
        log.info("获取题目列表表单，第{}页，每页{}条",query.getPageNo(),query.getPageSize());
        PageVO<ProblemTableVO> pageResult=problemService.pageQuery(query);
        return Result.ok(pageResult);
    }

    /**
     * 获取比赛列表表单 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @GetMapping("/contest-list")
    public Result<PageVO<ContestTableVO>> contestList(@Validated ContestPageQuery query) {
        log.info("获取比赛列表表单，第{}页，每页{}条",query.getPageNo(),query.getPageSize());
        PageVO<ContestTableVO> pageResult=contestService.pageQuery(query);
        return Result.ok(pageResult);
    }

    /**
     * 获取公告列表
     * @return 公告列表
     */
    @GetMapping("/announcement-list")
    public Result<List<AnnouncementVO>> announcementList() {
        log.info("获取公告列表");
        //查询未过期的可见系统公告
        List<Announcement> list = announcementService.lambdaQuery()
                .select(Announcement::getTitle, Announcement::getContent,
                        Announcement::getIsPinned, Announcement::getPublishTime,
                        Announcement::getExpireTime, Announcement::getLinkUrl, Announcement::getAuthorUsername)
                .eq(Announcement::getType, 0)
                .eq(Announcement::getIsVisible, 1)
                .and(o -> o.isNull(Announcement::getExpireTime)
                        .or()
                        .ge(Announcement::getExpireTime, LocalDateTime.now()))
                .orderByDesc(Announcement::getIsPinned, Announcement::getPriority, Announcement::getPublishTime)
                .list();
        //拷贝未vo列表
        List<AnnouncementVO> voList = BeanUtil.copyToList(list, AnnouncementVO.class);
        return Result.ok(voList);
    }

    /**
     * 获取评测结果列表 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @GetMapping("/submission-list")
    public Result<PageVO<SubmissionTableVO>> submissionList(@Validated SubmissionPageQuery query) {
        log.info("获取评测列表表单，第{}页，每页{}条",query.getPageNo(),query.getPageSize());
        PageVO<SubmissionTableVO> list=submissionService.getPublicSubmissionList(query);
        return Result.ok(list);
    }

}
