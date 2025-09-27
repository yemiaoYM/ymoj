package com.ymcloud.pojo.query.admin;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query.admin
 * @ClassName: AdminAnnouncementPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-09-27
 * @Description: 管理端公告分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAnnouncementPageQuery extends PageQuery {

    /**
     * 公告标题（模糊搜索）
     */
    @Size(max = 100, message = "公告标题长度不能超过100个字符")
    private String title;

    /**
     * 是否置顶：true=置顶，false=不置顶，null=全部
     */
    private Boolean pinned;

    /**
     * 是否可见：true=可见，false=不可见，null=全部
     */
    private Boolean status;
}
