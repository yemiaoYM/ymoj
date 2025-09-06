package com.ymcloud.web.task;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.result.Result;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.web.mapper.UserAcproblemMapper;
import com.ymcloud.pojo.vo.UserAcRankVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.task
 * @ClassName: UserAcRankTask
 * @Author: Yemiao
 * @CreateTime: 2025-08-07  18:01
 * @Description: 用户ac排名刷新定时任务
 */
@Component
@Slf4j
public class UserAcRankTask {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private UserAcproblemMapper userAcproblemMapper;

    /**
     * 处理用户ac排名刷新定时任务(每天凌晨一点进行刷新)
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void refreshUserAcRank() {
        log.info("刷新用户ac排名");
        //从用户ac表中取数据
        List<UserAcRankVO> userAcRankVOS = userAcproblemMapper.selectUserAcRank();
        //这里注意时构造结果信息，于基于注释方式返回的类型保持一致
        Result<List<UserAcRankVO>> result=Result.ok(userAcRankVOS);
        //向redis中写入
        String key=CacheConstants.CACHE_NAME+":"+CacheConstants.USER_AC_RANK_KEY;
        redisCache.setCacheObject(key, result);
    }

}
