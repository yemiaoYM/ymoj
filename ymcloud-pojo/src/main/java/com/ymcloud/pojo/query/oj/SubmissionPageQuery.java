package com.ymcloud.pojo.query.oj;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: SubmissionPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  17:47
 * @Description: 公共评测结果列表分页查询实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SubmissionPageQuery extends PageQuery {
    /**
     * 是否仅查询当前用户提交记录
     */
    @NotNull(message = "请指定是否仅查询当前用户记录")
    private Boolean isMine;
    /**
     * 评测状态
     */
    private Integer status;
    /**
     * 题目或题目唯一标识
     */
    private String problemKey;
    /**
     * 用户名
     */
    private String username;
}
