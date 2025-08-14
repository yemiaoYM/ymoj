package com.ymcloud.web.controller.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.vo.ContestVO;
import com.ymcloud.web.service.ContestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @PackageName: com.ymcloud.web.controller.user
 * @ClassName: ContestController
 * @Author: Yemiao
 * @CreateTime: 2025-08-13  01:16
 * @Description: 比赛相关业务接口
 */
@RestController
@Slf4j
@RequestMapping("/contest")
public class ContestController {

    @Autowired
    private ContestService contestService;

    /**
     * 获取比赛详情
     * @param contestId 比赛id
     * @return 比赛详情
     */
    @GetMapping
    public Result<ContestVO> getContestInfo(@RequestParam(value = "contestId") Long contestId) {
        log.info("获取比赛详情:{}", contestId);
        ContestVO contest=contestService.getContestInfo(contestId);
        return Result.ok(contest);
    }

    /**
     * 注册进入比赛
     * @param registerContestDTO 请求体
     */
    @PostMapping("/register")
    public Result<?> registerContest(@Validated @RequestBody RegisterContestDTO registerContestDTO) {
        log.info("注册进入比赛");
        contestService.registerContest(registerContestDTO);
        return Result.ok().msg("进入比赛成功");
    }





}
