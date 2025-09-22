package com.ymcloud.pojo.query.admin;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query.admin
 * @ClassName: AdminProblemPageQuery
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
     * 难度：0=未评定，1=入门，2=普及，3=提高，4=省选及以上
     */
    private Integer difficulty;

    /**
     * 可见性：1=公开，2=私有，3=比赛专用
     */
    private Integer visibility;
}
