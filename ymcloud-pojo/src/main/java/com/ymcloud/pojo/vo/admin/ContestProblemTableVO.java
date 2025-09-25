package com.ymcloud.pojo.vo.admin;

import lombok.Data;

import java.io.Serializable;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: ContestProblemTableVO
 * @Author: Yemiao
 * @CreateTime: 2025-09-25  18:49
 * @Description: 比赛题目列表实体
 */
@Data
public class ContestProblemTableVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 比赛题目记录id
     */
    private Long id;
    /**
     * 该题目在比赛中的顺序id
     */
    private String displayId;
    /**
     * 该题目在比赛中的标题，默认为原名字
     */
    private String displayTitle;
    /**
     * 气球颜色
     */
    private String color;
    /**
     * 题目id
     */
    private Long problemId;
    /**
     * 该题目原展示id
     */
    private String problemDisplayId;
    /**
     * 该题目原名字
     */
    private String problemDisplayTitle;
    /**
     * 出题人用户名
     */
    private String authorUsername;
}
