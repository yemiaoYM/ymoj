package com.ymcloud.pojo.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: ContestProblemSubmissionPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-08-15  22:27
 * @Description: 用户当前比赛题目评测结果列表分页查询实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ContestProblemSubmissionPageQuery extends PageQuery{

    /**
     * 题目唯一标识
     */
    @NotBlank(message = "题目序号不能为空")
    private String displayId;
    /**
     * 比赛id
     */
    @NotNull(message = "比赛id不能为空")
    private Long contestId;


}
