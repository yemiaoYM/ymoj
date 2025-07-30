package com.ymcloud.pojo.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.query
 * @ClassName: ContestPageQuery
 * @Author: Yemiao
 * @CreateTime: 2025-07-23  00:44
 * @Description: 比赛分页查询
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ContestPageQuery extends PageQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 关键字
     */
    private String keyword;
}
