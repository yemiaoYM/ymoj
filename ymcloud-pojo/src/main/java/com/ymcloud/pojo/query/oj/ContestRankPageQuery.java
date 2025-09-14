package com.ymcloud.pojo.query.oj;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: ContestRankPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-08-18  19:01
 * @Description: 比赛排名列表查询
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ContestRankPageQuery extends PageQuery {

    /**
     * 比赛id
     */
    @NotNull(message = "比赛id不能为空")
    private Long contestId;

    /**
     * 用户名称或者学校名称
     */
    private String keyword;
}
