package com.ymcloud.pojo.query;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: ProblemSubmissionPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-08-08  16:14
 * @Description: 用户当前评测结果列表分页查询实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ProblemSubmissionPageQuery extends PageQuery {

    /**
     * 题目唯一标识
     */
    @NotNull(message = "题目id不能为空")
    private String problemId;

}
