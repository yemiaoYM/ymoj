package com.ymcloud.pojo.query;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

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
    @Size(min = 2, max = 20, message = "用户名长度在2到20之间")
    private String username;
}
