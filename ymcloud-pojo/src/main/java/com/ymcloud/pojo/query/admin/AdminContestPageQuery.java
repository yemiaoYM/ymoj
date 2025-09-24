package com.ymcloud.pojo.query.admin;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query.admin
 * @ClassName: AdminContestPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  19:30
 * @Description: 管理端比赛分页查询
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AdminContestPageQuery extends PageQuery {

    /**
     * 关键词（比赛标题/创建者） 支持模糊查询
     */
    @Size(max = 50, message = "关键词长度不能超过50")
    private String keyword;

}
