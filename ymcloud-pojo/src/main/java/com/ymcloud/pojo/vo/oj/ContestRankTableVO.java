package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestRankTableVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-18  17:13
 * @Description: 比赛排名
 */
@Data
public class ContestRankTableVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 比赛模式 ACM还是IO
     */
    private Integer mode;
    /**
     * 比赛题目信息
     */
    private List<ContestProblemVO> problemInfoData;
    /**
     * 用户排名信息
     */
    private List<ContestUserRankVO> userRankData;
    /**
     * 总记录数
     */
    private long total;
}
