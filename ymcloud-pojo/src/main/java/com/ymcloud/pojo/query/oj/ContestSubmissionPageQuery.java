package com.ymcloud.pojo.query.oj;

import com.ymcloud.pojo.query.PageQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: ContestSubmissionPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-08-15  23:38
 * @Description: 比赛评测列表分页实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ContestSubmissionPageQuery extends PageQuery {
    /**
     * 比赛id
     */
    @NotNull(message = "比赛id不能为空")
    private Long contestId;
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
