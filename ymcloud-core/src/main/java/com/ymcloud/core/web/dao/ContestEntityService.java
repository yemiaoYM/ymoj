package com.ymcloud.core.web.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ymcloud.pojo.entity.Contest;

/**
 * @PackageName: com.ymcloud.core.web.dao
 * @ClassName: ContestEntityService
 * @Author: Yemiao
 * @CreateTime: 2025-08-15  23:05
 * @Description: ContestEntityService
 */
public interface ContestEntityService extends IService<Contest> {

    /**
     * 根据主键获取比赛信息
     * @param contestId 比赛id
     * @return 比赛信息
     */
    Contest getInfoById(Long contestId);
}
