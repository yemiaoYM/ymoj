package com.ymcloud.pojo.query.oj;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
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
     * 必须同时包含的标签 ID，省略或空集合表示不筛选，重复 ID 自动合并
     */
    @NotNull
    @Size(max = 100, message = "最多选择100个标签")
    private Set<@NotNull @Positive Long> tagIds = Set.of();
}
