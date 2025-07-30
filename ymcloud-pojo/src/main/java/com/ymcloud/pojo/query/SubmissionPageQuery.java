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
 * @Description: 评测结果列表分页查询实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SubmissionPageQuery extends PageQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 查询个人0还是所有人1
     */
    @NotNull(message = "需指定查询范围")
    private Integer scope=1;
    /**
     * 评测状态
     */
    private Integer status;
    /**
     * 题目标识(P1000)
     */
    private String problemId;
    /**
     * 题目标签
     */
    private String problemTitle;
    /**
     * 用户名
     */
    @Size(min = 2, max = 20, message = "用户名长度在2到20之间")
    private String username;

}
