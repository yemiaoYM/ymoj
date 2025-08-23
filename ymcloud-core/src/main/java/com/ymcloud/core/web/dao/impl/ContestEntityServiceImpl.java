package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.mapper.ContestMapper;
import com.ymcloud.pojo.entity.Contest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: ContestEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-15  23:05
 * @Description: ContestEntityServiceImpl
 */
@Service
public class ContestEntityServiceImpl extends ServiceImpl<ContestMapper, Contest>
        implements ContestEntityService {

    @Autowired
    private RedisCache redisCache;

    /**
     * 根据主键获取比赛信息
     * @param contestId 比赛id
     * @return 比赛信息
     */
    @Override
    public Contest getInfoById(Long contestId) {
        //简单校验
        if (contestId == null || contestId <= 0) {
            throw new ContestException("比赛id参数格式错误", HttpStatus.BAD_REQUEST);
        }
        //先查缓存
        String key = CacheConstants.CACHE_NAME + ":" + CacheConstants.CONTEST_KEY + ":" + contestId;
        Contest contest = redisCache.getCacheObject(key);
        //有数据直接返回
        if (contest != null) {
            return contest;
        }
        if (redisCache.hasKey(key)) {
            //已缓存空值，抛出异常
            throw new ContestNotExistException();
        }
        //没有就从数据库中查
        contest = getById(contestId);
        if (contest != null) {
            //写长期缓存
            redisCache.setCacheObject(key, contest);
        } else {
            //写入短期null值，防止缓存穿透
            redisCache.setCacheObject(key, null, 1, TimeUnit.MINUTES);
        }
        //如果仍然不存在抛出异常
        if (contest == null) {
            throw new ContestNotExistException();
        }
        return contest;
    }


}
