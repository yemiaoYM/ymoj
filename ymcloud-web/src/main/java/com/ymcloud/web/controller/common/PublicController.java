package com.ymcloud.web.controller.common;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.pojo.query.oj.ContestPageQuery;
import com.ymcloud.pojo.query.oj.ProblemPageQuery;
import com.ymcloud.pojo.query.oj.SubmissionPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.common.SystemSettingVO;
import com.ymcloud.pojo.vo.oj.AnnouncementVO;
import com.ymcloud.pojo.vo.oj.ContestTableVO;
import com.ymcloud.pojo.vo.oj.LanguageTableVO;
import com.ymcloud.pojo.vo.oj.ProblemTableVO;
import com.ymcloud.pojo.vo.oj.SubmissionTableVO;
import com.ymcloud.pojo.vo.oj.UserAcRankVO;
import com.ymcloud.pojo.vo.oj.UserCalendarHeatmapVO;
import com.ymcloud.pojo.vo.oj.UserInfoVO;
import com.ymcloud.web.service.common.PublicResourceService;
import com.ymcloud.web.service.common.SystemSettingService;
import com.ymcloud.web.service.oj.AnnouncementService;
import com.ymcloud.web.service.oj.ContestService;
import com.ymcloud.web.service.oj.ProblemService;
import com.ymcloud.web.service.oj.SubmissionService;
import com.ymcloud.web.service.oj.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private PublicResourceService publicResourceService;

    @Autowired
    private UserService userService;

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ContestService contestService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SystemSettingService systemSettingService;

    /**
     * 获取公开的系统配置
     * @return 注册开关
     */
    @GetMapping("/system-setting")
    public Result<SystemSettingVO> getSystemSetting() {
        log.debug("读取公开系统配置");
        return Result.ok(systemSettingService.getSystemSetting());
    }

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
    @Cacheable(cacheNames = CacheConstants.CACHE_NAME, key = "'" + CacheConstants.TAG_LIST_KEY + "'")
    @GetMapping("/tags")
    public Result<List<Tag>> listTags() {
        log.info("获取标签列表");
        return Result.ok(publicResourceService.listTags());
    }

    /**
     * 获取题目列表表单 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @GetMapping("/problems")
    public Result<PageVO<ProblemTableVO>> listProblems(@Validated ProblemPageQuery query) {
        log.info("获取题目列表表单，{}",query);
        PageVO<ProblemTableVO> pageResult=problemService.pageQuery(query);
        return Result.ok(pageResult);
    }

    /**
     * 获取比赛列表表单 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @GetMapping("/contests")
    public Result<PageVO<ContestTableVO>> listContests(@Validated ContestPageQuery query) {
        log.info("获取比赛列表表单，{}",query);
        PageVO<ContestTableVO> pageResult=contestService.pageQuery(query);
        return Result.ok(pageResult);
    }

    /**
     * 分页获取可见公告，排序由服务端固定。
     * @param pageNo 页码，默认1；每页固定5条
     * @return 公告分页数据
     */
    @GetMapping("/announcements")
    public Result<PageVO<AnnouncementVO>> listAnnouncements(@RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo) {
        log.debug("读取公告列表，页码：{}", pageNo);
        return Result.ok(announcementService.pageQuery(pageNo));
    }

    /**
     * 获取评测结果列表 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @GetMapping("/submissions")
    public Result<PageVO<SubmissionTableVO>> listSubmissions(@Validated SubmissionPageQuery query) {
        log.info("获取评测列表表单:{}",query);
        PageVO<SubmissionTableVO> list=submissionService.getPublicSubmissionList(query);
        return Result.ok(list);
    }

    /**
     * 获取本地编程语言列表
     * @return 编程语言列表
     */
    @Cacheable(cacheNames = CacheConstants.CACHE_NAME,key = "'language_list'")
    @GetMapping("/languages")
    public Result<List<LanguageTableVO>> listLanguages(){
        log.info("获取本地编程语言列表");
        return Result.ok(publicResourceService.listLanguages());
    }

    /**
     * 用户ac数量排名（每日凌晨1点更新）
     * @return 用户ac数量排名列表
     */
    @Cacheable(cacheNames = CacheConstants.CACHE_NAME,key = "'" + CacheConstants.USER_AC_RANK_KEY + "'")
    @GetMapping("/user-ac-rank")
    public Result<List<UserAcRankVO>> userAcRank() {
        log.info("获取用户ac数量排名（每日凌晨1点更新）");
        return Result.ok(publicResourceService.listUserAcRank());
    }

}
