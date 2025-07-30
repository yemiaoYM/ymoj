package com.ymcloud.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: ContestCountVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-25  01:25
 * @Description: 比赛参与人数
 */
@Data
public class ContestCountVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 比赛id
     */
    private Long id;
    /**
     * 参与人数
     */
    private Integer count;
}
