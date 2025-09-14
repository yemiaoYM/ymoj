package com.ymcloud.pojo.query.oj;

import com.ymcloud.pojo.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: ProblemPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  20:19
 * @Description: 题目分页查询
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ProblemPageQuery extends PageQuery {
    /**
     * 题目/题号 支持模糊查询
     */
    private String keyword;
    /**
     * 难度
     */
    private Integer difficulty;
    /**
     * 来源
     */
    private String source;
}
