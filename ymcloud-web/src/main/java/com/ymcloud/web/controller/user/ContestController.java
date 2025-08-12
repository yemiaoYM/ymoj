package com.ymcloud.web.controller.user;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.vo.ContestVO;
import com.ymcloud.web.service.ContestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}
