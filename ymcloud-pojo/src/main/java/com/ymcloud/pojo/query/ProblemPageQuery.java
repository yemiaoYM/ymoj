package com.ymcloud.pojo.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: ProblemPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  20:19
 * @Description: 题目分页查询
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ProblemPageQuery extends PageQuery{
    /**
     * 题目 支持模糊查询
     */
    private String title;
    /**
     * 根据标签查询
     */
    private List<Long> tagIds=List.of();
    /**
     * 难度
     */
    private Integer difficulty;
    /**
     * 来源
     */
    private String source;
}
