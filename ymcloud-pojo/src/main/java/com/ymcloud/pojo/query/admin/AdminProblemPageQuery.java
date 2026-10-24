package com.ymcloud.pojo.query.admin;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:30
 * @Description: 管理端题目分页查询
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AdminProblemPageQuery extends PageQuery {

    /**
     * 题目/题号 支持模糊查询
     */
    @Size(max = 50, message = "关键词长度不能超过50")
    private String keyword;

    /**
     * 难度：1=基础，2=进阶，3=挑战，4=高阶，5=大师；null 不筛选
     */
    @Min(value = 1, message = "难度参数非法")
    @Max(value = 5, message = "难度参数非法")
    private Integer difficulty;

    /**
     * 可见性：1=公开，2=私有，3=比赛专用
     */
    private Integer visibility;
}
